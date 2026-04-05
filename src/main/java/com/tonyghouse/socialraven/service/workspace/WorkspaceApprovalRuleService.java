package com.tonyghouse.socialraven.service.workspace;

import com.tonyghouse.socialraven.constant.PostCollectionType;
import com.tonyghouse.socialraven.constant.WorkspaceApprovalMode;
import com.tonyghouse.socialraven.constant.WorkspaceApprovalRuleScope;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceApprovalRuleRequest;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceApprovalRuleResponse;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.WorkspaceApprovalRuleEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.WorkspaceApprovalRuleRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceApprovalRuleService {

    @Autowired
    private WorkspaceApprovalRuleRepo workspaceApprovalRuleRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private AccountProfileService accountProfileService;

    @Transactional(readOnly = true)
    public List<WorkspaceApprovalRuleResponse> getRules(String workspaceId) {
        return workspaceApprovalRuleRepo.findAllByWorkspaceIdOrderByScopeTypeAscScopeValueAsc(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<WorkspaceApprovalRuleResponse> replaceRules(String workspaceId,
                                                            Collection<WorkspaceApprovalRuleRequest> requestedRules) {
        requireWorkspace(workspaceId);

        List<WorkspaceApprovalRuleEntity> existing =
                workspaceApprovalRuleRepo.findAllByWorkspaceIdOrderByScopeTypeAscScopeValueAsc(workspaceId);
        Map<RuleKey, WorkspaceApprovalRuleEntity> existingByKey = existing.stream()
                .collect(Collectors.toMap(
                        entity -> new RuleKey(entity.getScopeType(), entity.getScopeValue()),
                        entity -> entity,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<String> validAccountIds = accountProfileService.getAllConnectedAccounts(workspaceId, false).stream()
                .map(ConnectedAccount::getProviderUserId)
                .collect(Collectors.toSet());

        Map<RuleKey, WorkspaceApprovalRuleEntity> desiredByKey = new LinkedHashMap<>();
        Collection<WorkspaceApprovalRuleRequest> requestedRuleCollection = requestedRules != null
                ? requestedRules
                : List.of();
        for (WorkspaceApprovalRuleRequest request : requestedRuleCollection) {
            NormalizedRule normalizedRule = normalizeRule(request, validAccountIds);
            RuleKey key = new RuleKey(normalizedRule.scopeType(), normalizedRule.scopeValue());
            if (desiredByKey.containsKey(key)) {
                throw new SocialRavenException(
                        "Approval rules cannot contain duplicate scope definitions",
                        HttpStatus.BAD_REQUEST
                );
            }

            WorkspaceApprovalRuleEntity entity = existingByKey.getOrDefault(key, new WorkspaceApprovalRuleEntity());
            OffsetDateTime now = OffsetDateTime.now();
            if (entity.getId() == null) {
                entity.setWorkspaceId(workspaceId);
                entity.setCreatedAt(now);
            }
            entity.setScopeType(normalizedRule.scopeType());
            entity.setScopeValue(normalizedRule.scopeValue());
            entity.setApprovalMode(normalizedRule.approvalMode());
            entity.setUpdatedAt(now);
            desiredByKey.put(key, entity);
        }

        List<WorkspaceApprovalRuleEntity> stale = existing.stream()
                .filter(entity -> !desiredByKey.containsKey(new RuleKey(entity.getScopeType(), entity.getScopeValue())))
                .toList();
        if (!stale.isEmpty()) {
            workspaceApprovalRuleRepo.deleteAll(stale);
        }

        return workspaceApprovalRuleRepo.saveAll(desiredByKey.values()).stream()
                .map(this::toResponse)
                .sorted((left, right) -> {
                    int scopeCompare = left.getScopeType().compareTo(right.getScopeType());
                    if (scopeCompare != 0) {
                        return scopeCompare;
                    }
                    return left.getScopeValue().compareToIgnoreCase(right.getScopeValue());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalRuleSnapshot getSnapshot(String workspaceId) {
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        WorkspaceApprovalMode workspaceMode = workspace.getApprovalMode() != null
                ? workspace.getApprovalMode()
                : WorkspaceApprovalMode.OPTIONAL;
        boolean autoScheduleAfterApproval = workspace.isAutoScheduleAfterApproval();

        Map<String, WorkspaceApprovalMode> accountModes = new LinkedHashMap<>();
        Map<PostCollectionType, WorkspaceApprovalMode> contentTypeModes = new EnumMap<>(PostCollectionType.class);

        for (WorkspaceApprovalRuleEntity entity : workspaceApprovalRuleRepo.findAllByWorkspaceIdOrderByScopeTypeAscScopeValueAsc(workspaceId)) {
            if (entity.getScopeType() == WorkspaceApprovalRuleScope.ACCOUNT) {
                accountModes.put(entity.getScopeValue(), entity.getApprovalMode());
            } else if (entity.getScopeType() == WorkspaceApprovalRuleScope.CONTENT_TYPE) {
                contentTypeModes.put(PostCollectionType.valueOf(entity.getScopeValue()), entity.getApprovalMode());
            }
        }

        return new ApprovalRuleSnapshot(workspaceMode, autoScheduleAfterApproval, accountModes, contentTypeModes);
    }

    public WorkspaceApprovalMode resolveApprovalMode(ApprovalRuleSnapshot snapshot, PostCollectionEntity collection) {
        if (collection != null && collection.getApprovalModeOverride() != null) {
            return collection.getApprovalModeOverride();
        }

        if (collection != null && collection.getPosts() != null && !collection.getPosts().isEmpty()) {
            WorkspaceApprovalMode matchedAccountMode = collection.getPosts().stream()
                    .map(post -> snapshot.accountModes().get(post.getProviderUserId()))
                    .filter(mode -> mode != null)
                    .max(this::compareApprovalMode)
                    .orElse(null);
            if (matchedAccountMode != null) {
                return matchedAccountMode;
            }
        }

        if (collection != null
                && collection.getPostCollectionType() != null
                && snapshot.contentTypeModes().containsKey(collection.getPostCollectionType())) {
            return snapshot.contentTypeModes().get(collection.getPostCollectionType());
        }

        return snapshot.workspaceMode();
    }

    private int compareApprovalMode(WorkspaceApprovalMode left, WorkspaceApprovalMode right) {
        return Integer.compare(approvalModeRank(left), approvalModeRank(right));
    }

    private int approvalModeRank(WorkspaceApprovalMode mode) {
        if (mode == null) {
            return -1;
        }
        return switch (mode) {
            case NONE -> 0;
            case OPTIONAL -> 1;
            case REQUIRED -> 2;
            case MULTI_STEP -> 3;
        };
    }

    private WorkspaceEntity requireWorkspace(String workspaceId) {
        return workspaceRepo.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));
    }

    private NormalizedRule normalizeRule(WorkspaceApprovalRuleRequest request, Set<String> validAccountIds) {
        if (request == null) {
            throw new SocialRavenException("Approval rule payload is required", HttpStatus.BAD_REQUEST);
        }

        WorkspaceApprovalRuleScope scopeType;
        try {
            scopeType = WorkspaceApprovalRuleScope.valueOf(
                    requireNonBlank(request.getScopeType(), "scopeType is required").toUpperCase(Locale.ENGLISH)
            );
        } catch (IllegalArgumentException e) {
            throw new SocialRavenException("Invalid approval rule scopeType", HttpStatus.BAD_REQUEST);
        }

        String normalizedScopeValue = requireNonBlank(request.getScopeValue(), "scopeValue is required");
        WorkspaceApprovalMode approvalMode;
        try {
            approvalMode = WorkspaceApprovalMode.valueOf(
                    requireNonBlank(request.getApprovalMode(), "approvalMode is required").toUpperCase(Locale.ENGLISH)
            );
        } catch (IllegalArgumentException e) {
            throw new SocialRavenException("Invalid approvalMode", HttpStatus.BAD_REQUEST);
        }

        if (scopeType == WorkspaceApprovalRuleScope.CONTENT_TYPE) {
            try {
                normalizedScopeValue = PostCollectionType.valueOf(normalizedScopeValue).name();
            } catch (IllegalArgumentException e) {
                throw new SocialRavenException("Content-type approval rules require IMAGE, VIDEO, or TEXT", HttpStatus.BAD_REQUEST);
            }
        } else if (!validAccountIds.contains(normalizedScopeValue)) {
            throw new SocialRavenException(
                    "Account approval rules must target a currently connected account",
                    HttpStatus.BAD_REQUEST
            );
        }

        return new NormalizedRule(scopeType, normalizedScopeValue, approvalMode);
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new SocialRavenException(message, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private WorkspaceApprovalRuleResponse toResponse(WorkspaceApprovalRuleEntity entity) {
        return new WorkspaceApprovalRuleResponse(
                entity.getId(),
                entity.getScopeType().name(),
                entity.getScopeValue(),
                entity.getApprovalMode().name()
        );
    }

    public record ApprovalRuleSnapshot(WorkspaceApprovalMode workspaceMode,
                                       boolean autoScheduleAfterApproval,
                                       Map<String, WorkspaceApprovalMode> accountModes,
                                       Map<PostCollectionType, WorkspaceApprovalMode> contentTypeModes) {
    }

    private record RuleKey(WorkspaceApprovalRuleScope scopeType, String scopeValue) {
    }

    private record NormalizedRule(WorkspaceApprovalRuleScope scopeType,
                                  String scopeValue,
                                  WorkspaceApprovalMode approvalMode) {
    }
}
