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
        return workspaceUserCapabilityRepo.findAllByWorkspaceIdAndCapability(workspaceId, WorkspaceCapability.APPROVE_POSTS)
                .stream()
                .map(WorkspaceUserCapabilityEntity::getUserId)
                .sorted()
                .toList();
    }

    @Transactional
    public List<String> replaceExplicitApprovers(String workspaceId, Collection<String> requestedUserIds) {
        List<String> normalizedApproverUserIds = normalizeExplicitApproverUserIds(workspaceId, requestedUserIds);
        Set<String> requestedIds = new LinkedHashSet<>(normalizedApproverUserIds);

        List<WorkspaceUserCapabilityEntity> existing = workspaceUserCapabilityRepo.findAllByWorkspaceIdAndCapability(
                workspaceId,
                WorkspaceCapability.APPROVE_POSTS
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
            entity.setCapability(WorkspaceCapability.APPROVE_POSTS);
            entity.setCreatedAt(now);
            workspaceUserCapabilityRepo.save(entity);
        }

        return normalizedApproverUserIds;
    }

    public List<String> normalizeExplicitApproverUserIds(String workspaceId, Collection<String> requestedUserIds) {
        Set<String> uniqueIds = requestedUserIds == null
                ? Set.of()
                : requestedUserIds.stream()
                .filter(userId -> userId != null && !userId.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, WorkspaceRole> workspaceRolesByUserId = workspaceMemberRepo.findAllByWorkspaceId(workspaceId).stream()
                .collect(Collectors.toMap(WorkspaceMemberEntity::getUserId, WorkspaceMemberEntity::getRole));

        List<String> normalized = uniqueIds.stream()
                .map(userId -> normalizeApproverUserId(userId, workspaceRolesByUserId))
                .filter(userId -> userId != null)
                .sorted()
                .toList();

        return normalized;
    }

    @Transactional
    public void clearOverridesForMember(String workspaceId, String userId) {
        workspaceUserCapabilityRepo.deleteAllByWorkspaceIdAndUserId(workspaceId, userId);
    }

    private String normalizeApproverUserId(String userId, Map<String, WorkspaceRole> workspaceRolesByUserId) {
        WorkspaceRole role = workspaceRolesByUserId.get(userId);
        if (role == null) {
            throw new SocialRavenException("Approver must be an active workspace member", HttpStatus.BAD_REQUEST);
        }
        if (role == WorkspaceRole.READ_ONLY) {
            throw new SocialRavenException("Read-only members cannot be assigned as approvers", HttpStatus.BAD_REQUEST);
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
                    WorkspaceCapability.SHARE_REVIEW_LINKS
            );
            case EDITOR -> EnumSet.of(WorkspaceCapability.REQUEST_CHANGES);
            case READ_ONLY -> EnumSet.noneOf(WorkspaceCapability.class);
        };
    }
}
