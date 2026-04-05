package com.tonyghouse.socialraven.service.workspace;

import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.entity.WorkspaceUserCapabilityEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.repo.WorkspaceUserCapabilityRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WorkspaceCapabilityService {

    @Autowired
    private WorkspaceUserCapabilityRepo workspaceUserCapabilityRepo;

    @Autowired
    private WorkspaceMemberRepo workspaceMemberRepo;

    public EnumSet<WorkspaceCapability> getEffectiveCapabilities(String workspaceId,
                                                                 String userId,
                                                                 WorkspaceRole role) {
        EnumSet<WorkspaceCapability> capabilities = defaultCapabilitiesForRole(role);

        if (role == WorkspaceRole.EDITOR) {
            workspaceUserCapabilityRepo.findAllByWorkspaceIdAndUserId(workspaceId, userId).stream()
                    .map(WorkspaceUserCapabilityEntity::getCapability)
                    .forEach(capabilities::add);
        }

        return capabilities;
    }

    public List<WorkspaceCapability> getEffectiveCapabilitiesList(String workspaceId,
                                                                  String userId,
                                                                  WorkspaceRole role) {
        return getEffectiveCapabilities(workspaceId, userId, role).stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    public boolean hasCapability(String workspaceId,
                                 String userId,
                                 WorkspaceRole role,
                                 WorkspaceCapability capability) {
        return getEffectiveCapabilities(workspaceId, userId, role).contains(capability);
    }

    public List<String> getExplicitApproverUserIds(String workspaceId) {
        return getExplicitUserIdsForCapability(workspaceId, WorkspaceCapability.APPROVE_POSTS);
    }

    public List<String> getExplicitPublisherUserIds(String workspaceId) {
        return getExplicitUserIdsForCapability(workspaceId, WorkspaceCapability.PUBLISH_POSTS);
    }

    @Transactional
    public List<String> replaceExplicitApprovers(String workspaceId, Collection<String> requestedUserIds) {
        return replaceExplicitEditorCapabilityAssignments(
                workspaceId,
                requestedUserIds,
                WorkspaceCapability.APPROVE_POSTS,
                "Read-only members cannot be assigned as approvers"
        );
    }

    @Transactional
    public List<String> replaceExplicitPublishers(String workspaceId, Collection<String> requestedUserIds) {
        return replaceExplicitEditorCapabilityAssignments(
                workspaceId,
                requestedUserIds,
                WorkspaceCapability.PUBLISH_POSTS,
                "Read-only members cannot be assigned as publishers"
        );
    }

    private List<String> getExplicitUserIdsForCapability(String workspaceId, WorkspaceCapability capability) {
        return workspaceUserCapabilityRepo.findAllByWorkspaceIdAndCapability(workspaceId, capability).stream()
                .map(WorkspaceUserCapabilityEntity::getUserId)
                .sorted()
                .toList();
    }

    private List<String> replaceExplicitEditorCapabilityAssignments(String workspaceId,
                                                                    Collection<String> requestedUserIds,
                                                                    WorkspaceCapability capability,
                                                                    String readOnlyMessage) {
        List<String> normalizedUserIds = normalizeExplicitEditorCapabilityUserIds(
                workspaceId,
                requestedUserIds,
                readOnlyMessage
        );
        Set<String> requestedIds = new LinkedHashSet<>(normalizedUserIds);

        List<WorkspaceUserCapabilityEntity> existing = workspaceUserCapabilityRepo.findAllByWorkspaceIdAndCapability(
                workspaceId,
                capability
        );

        List<WorkspaceUserCapabilityEntity> staleAssignments = existing.stream()
                .filter(entity -> !requestedIds.contains(entity.getUserId()))
                .toList();
        if (!staleAssignments.isEmpty()) {
            workspaceUserCapabilityRepo.deleteAll(staleAssignments);
        }

        Set<String> existingUserIds = existing.stream()
                .map(WorkspaceUserCapabilityEntity::getUserId)
                .collect(Collectors.toSet());

        OffsetDateTime now = OffsetDateTime.now();
        for (String userId : requestedIds) {
            if (existingUserIds.contains(userId)) {
                continue;
            }

            WorkspaceUserCapabilityEntity entity = new WorkspaceUserCapabilityEntity();
            entity.setWorkspaceId(workspaceId);
            entity.setUserId(userId);
            entity.setCapability(capability);
            entity.setCreatedAt(now);
            workspaceUserCapabilityRepo.save(entity);
        }

        return normalizedUserIds;
    }

    public List<String> normalizeExplicitApproverUserIds(String workspaceId, Collection<String> requestedUserIds) {
        return normalizeExplicitEditorCapabilityUserIds(
                workspaceId,
                requestedUserIds,
                "Read-only members cannot be assigned as approvers"
        );
    }

    private List<String> normalizeExplicitEditorCapabilityUserIds(String workspaceId,
                                                                  Collection<String> requestedUserIds,
                                                                  String readOnlyMessage) {
        Set<String> uniqueIds = requestedUserIds == null
                ? Set.of()
                : requestedUserIds.stream()
                .filter(userId -> userId != null && !userId.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, WorkspaceRole> workspaceRolesByUserId = workspaceMemberRepo.findAllByWorkspaceId(workspaceId).stream()
                .collect(Collectors.toMap(WorkspaceMemberEntity::getUserId, WorkspaceMemberEntity::getRole));

        List<String> normalized = uniqueIds.stream()
                .map(userId -> normalizeExplicitEditorCapabilityUserId(userId, workspaceRolesByUserId, readOnlyMessage))
                .filter(userId -> userId != null)
                .sorted()
                .toList();

        return normalized;
    }

    @Transactional
    public void clearOverridesForMember(String workspaceId, String userId) {
        workspaceUserCapabilityRepo.deleteAllByWorkspaceIdAndUserId(workspaceId, userId);
    }

    private String normalizeExplicitEditorCapabilityUserId(String userId,
                                                           Map<String, WorkspaceRole> workspaceRolesByUserId,
                                                           String readOnlyMessage) {
        WorkspaceRole role = workspaceRolesByUserId.get(userId);
        if (role == null) {
            throw new SocialRavenException("Capability assignee must be an active workspace member", HttpStatus.BAD_REQUEST);
        }
        if (role == WorkspaceRole.READ_ONLY) {
            throw new SocialRavenException(readOnlyMessage, HttpStatus.BAD_REQUEST);
        }
        if (role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN) {
            return null;
        }
        return userId;
    }

    private EnumSet<WorkspaceCapability> defaultCapabilitiesForRole(WorkspaceRole role) {
        if (role == null) {
            return EnumSet.noneOf(WorkspaceCapability.class);
        }

        return switch (role) {
            case OWNER, ADMIN -> EnumSet.of(
                    WorkspaceCapability.APPROVE_POSTS,
                    WorkspaceCapability.PUBLISH_POSTS,
                    WorkspaceCapability.REQUEST_CHANGES,
                    WorkspaceCapability.MANAGE_APPROVAL_RULES,
                    WorkspaceCapability.SHARE_REVIEW_LINKS,
                    WorkspaceCapability.MANAGE_ASSET_LIBRARY,
                    WorkspaceCapability.EXPORT_CLIENT_REPORTS
            );
            case EDITOR -> EnumSet.of(WorkspaceCapability.REQUEST_CHANGES);
            case READ_ONLY -> EnumSet.noneOf(WorkspaceCapability.class);
        };
    }
}
