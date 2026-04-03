package com.tonyghouse.socialraven.service.workspace;

import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.constant.RecoveryState;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.workspace.AgencyOpsResponse;
import com.tonyghouse.socialraven.entity.CompanyEntity;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.entity.WorkspaceUserCapabilityEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.CompanyRepo;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.repo.WorkspaceUserCapabilityRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgencyOpsService {

    private static final int APPROVAL_RISK_WINDOW_HOURS = 48;
    private static final int SCHEDULED_RISK_WINDOW_DAYS = 7;

    @Autowired
    private WorkspaceMemberRepo workspaceMemberRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private CompanyRepo companyRepo;

    @Autowired
    private PostCollectionRepo postCollectionRepo;

    @Autowired
    private WorkspaceCapabilityService workspaceCapabilityService;

    @Autowired
    private WorkspaceUserCapabilityRepo workspaceUserCapabilityRepo;

    @Autowired
    private ClerkUserService clerkUserService;

    @Transactional(readOnly = true)
    public AgencyOpsResponse getAgencyOps(String userId) {
        Map<String, WorkspaceRole> roleByWorkspaceId = loadUserWorkspaceRoles(userId);
        if (roleByWorkspaceId.isEmpty()) {
            throw new SocialRavenException("Approval workflow access is required", HttpStatus.FORBIDDEN);
        }

        List<WorkspaceEntity> activeWorkspaces = StreamSupport
                .stream(workspaceRepo.findAllById(roleByWorkspaceId.keySet()).spliterator(), false)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .sorted(Comparator.comparing(WorkspaceEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (activeWorkspaces.isEmpty()) {
            return emptyResponse();
        }

        Map<String, CompanyEntity> companiesById = StreamSupport
                .stream(companyRepo.findAllById(activeWorkspaces.stream().map(WorkspaceEntity::getCompanyId).collect(Collectors.toSet())).spliterator(), false)
                .collect(Collectors.toMap(CompanyEntity::getId, company -> company));

        Map<String, WorkspaceScope> scopesByWorkspaceId = new LinkedHashMap<>();
        boolean hasWorkflowAccess = false;
        for (WorkspaceEntity workspace : activeWorkspaces) {
            WorkspaceRole role = roleByWorkspaceId.get(workspace.getId());
            EnumSet<WorkspaceCapability> capabilities = workspaceCapabilityService.getEffectiveCapabilities(
                    workspace.getId(),
                    userId,
                    role
            );
            if (capabilities.contains(WorkspaceCapability.APPROVE_POSTS)
                    || capabilities.contains(WorkspaceCapability.REQUEST_CHANGES)) {
                hasWorkflowAccess = true;
            }

            CompanyEntity company = companiesById.get(workspace.getCompanyId());
            scopesByWorkspaceId.put(
                    workspace.getId(),
                    new WorkspaceScope(
                            workspace.getId(),
                            workspace.getName(),
                            company != null ? company.getName() : null
                    )
            );
        }

        if (!hasWorkflowAccess) {
            throw new SocialRavenException("Approval workflow access is required", HttpStatus.FORBIDDEN);
        }

        List<String> workspaceIds = new ArrayList<>(scopesByWorkspaceId.keySet());
        Map<String, List<WorkspaceMemberEntity>> membersByWorkspaceId = workspaceMemberRepo.findAllByWorkspaceIdIn(workspaceIds)
                .stream()
                .collect(Collectors.groupingBy(
                        WorkspaceMemberEntity::getWorkspaceId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<String, Set<String>> approverUserIdsByWorkspaceId = buildApproverUserIds(workspaceIds, membersByWorkspaceId);
        Map<String, Set<String>> workspaceIdsByApproverUserId = invertApproverAssignments(approverUserIdsByWorkspaceId);
        Map<String, ProfileSnapshot> profilesByUserId = resolveProfiles(workspaceIdsByApproverUserId.keySet());

        OffsetDateTime now = OffsetDateTime.now();
        List<PostCollectionEntity> reviewCollections = postCollectionRepo.findAgencyOpsReviewCollections(
                workspaceIds,
                List.of(PostReviewStatus.IN_REVIEW, PostReviewStatus.CHANGES_REQUESTED)
        );
        List<PostCollectionEntity> scheduledCollections = postCollectionRepo.findAgencyOpsScheduledCollections(
                workspaceIds,
                now.minusHours(1),
                now.plusDays(SCHEDULED_RISK_WINDOW_DAYS)
        );

        Map<String, WorkspaceHealthAccumulator> workspaceHealth = initializeWorkspaceHealth(scopesByWorkspaceId.values());
        Map<String, WorkloadAccumulator> workloadByUserId = new LinkedHashMap<>();
        List<AgencyOpsResponse.QueueItem> queue = new ArrayList<>();
        List<AgencyOpsResponse.QueueItem> overdueQueue = new ArrayList<>();
        List<AgencyOpsResponse.PublishRiskItem> publishRisk = new ArrayList<>();
        Set<Long> riskCollectionIds = new LinkedHashSet<>();

        for (PostCollectionEntity collection : reviewCollections) {
            WorkspaceScope scope = scopesByWorkspaceId.get(collection.getWorkspaceId());
            if (scope == null) {
                continue;
            }

            WorkspaceHealthAccumulator health = workspaceHealth.get(scope.workspaceId());
            if (collection.getReviewStatus() == PostReviewStatus.IN_REVIEW) {
                String attentionStatus = resolveAttentionStatus(collection, now);
                AgencyOpsResponse.QueueItem queueItem = toQueueItem(
                        collection,
                        scope,
                        approverUserIdsByWorkspaceId.getOrDefault(scope.workspaceId(), Set.of()),
                        attentionStatus
                );
                queue.add(queueItem);
                health.pendingApprovalCount++;

                if ("ESCALATED".equals(attentionStatus)) {
                    overdueQueue.add(queueItem);
                    health.escalatedApprovalCount++;
                } else if ("OVERDUE".equals(attentionStatus)) {
                    overdueQueue.add(queueItem);
                    health.overdueApprovalCount++;
                }

                assignWorkload(
                        workloadByUserId,
                        approverUserIdsByWorkspaceId.getOrDefault(scope.workspaceId(), Set.of()),
                        workspaceIdsByApproverUserId,
                        profilesByUserId,
                        collection,
                        attentionStatus
                );

                if (isApprovalRisk(collection, now) && riskCollectionIds.add(collection.getId())) {
                    publishRisk.add(toApprovalRiskItem(
                            collection,
                            scope,
                            attentionStatus,
                            approverUserIdsByWorkspaceId.getOrDefault(scope.workspaceId(), Set.of()),
                            now
                    ));
                }
            } else if (collection.getReviewStatus() == PostReviewStatus.CHANGES_REQUESTED) {
                health.changesRequestedCount++;
                if (isApprovalRisk(collection, now) && riskCollectionIds.add(collection.getId())) {
                    publishRisk.add(toChangesRequestedRiskItem(
                            collection,
                            scope,
                            approverUserIdsByWorkspaceId.getOrDefault(scope.workspaceId(), Set.of()),
                            now
                    ));
                }
            }
        }

        for (PostCollectionEntity collection : scheduledCollections) {
            WorkspaceScope scope = scopesByWorkspaceId.get(collection.getWorkspaceId());
            if (scope == null || !isScheduledRisk(collection) || !riskCollectionIds.add(collection.getId())) {
                continue;
            }
            publishRisk.add(toScheduledRiskItem(collection, scope));
        }

        Map<String, Long> riskCountByWorkspaceId = publishRisk.stream()
                .collect(Collectors.groupingBy(
                        AgencyOpsResponse.PublishRiskItem::getWorkspaceId,
                        Collectors.counting()
                ));
        for (WorkspaceHealthAccumulator health : workspaceHealth.values()) {
            health.atRiskPublishCount = riskCountByWorkspaceId.getOrDefault(health.workspaceId, 0L).intValue();
            health.healthStatus = resolveHealthStatus(health);
        }

        queue.sort(queueComparator());
        overdueQueue.sort(queueComparator());
        publishRisk.sort(publishRiskComparator());

        List<AgencyOpsResponse.ApproverWorkload> workload = workloadByUserId.values().stream()
                .map(accumulator -> accumulator.toResponse())
                .filter(item -> item.getPendingApprovalCount() > 0)
                .sorted(Comparator
                        .comparingInt(AgencyOpsResponse.ApproverWorkload::getEscalatedApprovalCount).reversed()
                        .thenComparingInt(AgencyOpsResponse.ApproverWorkload::getOverdueApprovalCount).reversed()
                        .thenComparingInt(AgencyOpsResponse.ApproverWorkload::getPendingApprovalCount).reversed()
                        .thenComparing(item -> safeLower(item.getDisplayName())))
                .toList();

        List<AgencyOpsResponse.WorkspaceHealth> workspaceHealthResponses = workspaceHealth.values().stream()
                .map(WorkspaceHealthAccumulator::toResponse)
                .sorted(Comparator
                        .comparingInt((AgencyOpsResponse.WorkspaceHealth item) -> healthRank(item.getHealthStatus()))
                        .thenComparing(item -> safeLower(item.getWorkspaceName())))
                .toList();

        List<AgencyOpsResponse.ApproverOption> approvers = workload.stream()
                .map(item -> new AgencyOpsResponse.ApproverOption(
                        item.getUserId(),
                        item.getDisplayName(),
                        item.getEmail(),
                        item.getWorkspaceCount()
                ))
                .toList();

        List<AgencyOpsResponse.WorkspaceOption> workspaces = scopesByWorkspaceId.values().stream()
                .map(scope -> new AgencyOpsResponse.WorkspaceOption(
                        scope.workspaceId(),
                        scope.workspaceName(),
                        scope.companyName()
                ))
                .toList();

        AgencyOpsResponse.Summary summary = new AgencyOpsResponse.Summary(
                workspaces.size(),
                queue.size(),
                (int) queue.stream().filter(item -> "OVERDUE".equals(item.getAttentionStatus())).count(),
                (int) queue.stream().filter(item -> "ESCALATED".equals(item.getAttentionStatus())).count(),
                publishRisk.size(),
                approvers.size()
        );

        return new AgencyOpsResponse(
                summary,
                workspaces,
                approvers,
                queue,
                overdueQueue,
                workload,
                workspaceHealthResponses,
                publishRisk
        );
    }

    private Map<String, WorkspaceRole> loadUserWorkspaceRoles(String userId) {
        Map<String, WorkspaceRole> roleByWorkspaceId = new LinkedHashMap<>();
        for (WorkspaceMemberEntity membership : workspaceMemberRepo.findAllByUserId(userId)) {
            roleByWorkspaceId.merge(
                    membership.getWorkspaceId(),
                    membership.getRole(),
                    (left, right) -> right.isAtLeast(left) ? right : left
            );
        }
        return roleByWorkspaceId;
    }

    private Map<String, WorkspaceHealthAccumulator> initializeWorkspaceHealth(Collection<WorkspaceScope> scopes) {
        Map<String, WorkspaceHealthAccumulator> response = new LinkedHashMap<>();
        for (WorkspaceScope scope : scopes) {
            response.put(scope.workspaceId(), new WorkspaceHealthAccumulator(scope));
        }
        return response;
    }

    private Map<String, Set<String>> buildApproverUserIds(List<String> workspaceIds,
                                                          Map<String, List<WorkspaceMemberEntity>> membersByWorkspaceId) {
        Map<String, Set<String>> explicitApproversByWorkspaceId = workspaceUserCapabilityRepo
                .findAllByWorkspaceIdInAndCapability(workspaceIds, WorkspaceCapability.APPROVE_POSTS)
                .stream()
                .collect(Collectors.groupingBy(
                        WorkspaceUserCapabilityEntity::getWorkspaceId,
                        LinkedHashMap::new,
                        Collectors.mapping(WorkspaceUserCapabilityEntity::getUserId, Collectors.toCollection(LinkedHashSet::new))
                ));

        Map<String, Set<String>> approverUserIdsByWorkspaceId = new LinkedHashMap<>();
        for (String workspaceId : workspaceIds) {
            Set<String> approvers = new LinkedHashSet<>();
            Set<String> explicitApprovers = explicitApproversByWorkspaceId.getOrDefault(workspaceId, Set.of());
            for (WorkspaceMemberEntity member : membersByWorkspaceId.getOrDefault(workspaceId, List.of())) {
                if (member.getRole() == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.ADMIN) {
                    approvers.add(member.getUserId());
                } else if (member.getRole() == WorkspaceRole.EDITOR && explicitApprovers.contains(member.getUserId())) {
                    approvers.add(member.getUserId());
                }
            }
            approverUserIdsByWorkspaceId.put(workspaceId, approvers);
        }
        return approverUserIdsByWorkspaceId;
    }

    private Map<String, Set<String>> invertApproverAssignments(Map<String, Set<String>> approverUserIdsByWorkspaceId) {
        Map<String, Set<String>> workspaceIdsByUserId = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : approverUserIdsByWorkspaceId.entrySet()) {
            for (String userId : entry.getValue()) {
                workspaceIdsByUserId.computeIfAbsent(userId, ignored -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
        return workspaceIdsByUserId;
    }

    private Map<String, ProfileSnapshot> resolveProfiles(Collection<String> userIds) {
        Map<String, ProfileSnapshot> profiles = new LinkedHashMap<>();
        for (String userId : userIds) {
            ClerkUserService.UserProfile profile = clerkUserService.getUserProfile(userId);
            if (profile == null) {
                profiles.put(userId, new ProfileSnapshot(userId, null));
                continue;
            }

            String firstName = profile.firstName() != null ? profile.firstName().trim() : "";
            String lastName = profile.lastName() != null ? profile.lastName().trim() : "";
            String email = profile.email() != null && !profile.email().isBlank() ? profile.email().trim() : null;
            String fullName = (firstName + " " + lastName).trim();
            String displayName = !fullName.isBlank() ? fullName : (email != null ? email : userId);
            profiles.put(userId, new ProfileSnapshot(displayName, email));
        }
        return profiles;
    }

    private void assignWorkload(Map<String, WorkloadAccumulator> workloadByUserId,
                                Set<String> approverUserIds,
                                Map<String, Set<String>> workspaceIdsByApproverUserId,
                                Map<String, ProfileSnapshot> profilesByUserId,
                                PostCollectionEntity collection,
                                String attentionStatus) {
        if (approverUserIds.isEmpty()) {
            return;
        }

        OffsetDateTime dueAt = firstNonNull(
                collection.getScheduledTime(),
                collection.getNextApprovalReminderAt(),
                collection.getReviewSubmittedAt()
        );

        for (String approverUserId : approverUserIds) {
            ProfileSnapshot profile = profilesByUserId.getOrDefault(approverUserId, new ProfileSnapshot(approverUserId, null));
            WorkloadAccumulator accumulator = workloadByUserId.computeIfAbsent(
                    approverUserId,
                    ignored -> new WorkloadAccumulator(
                            approverUserId,
                            profile.displayName(),
                            profile.email(),
                            workspaceIdsByApproverUserId.getOrDefault(approverUserId, Set.of()).size()
                    )
            );
            accumulator.pendingApprovalCount++;
            if ("ESCALATED".equals(attentionStatus)) {
                accumulator.escalatedApprovalCount++;
            } else if ("OVERDUE".equals(attentionStatus)) {
                accumulator.overdueApprovalCount++;
            }
            if (dueAt != null && (accumulator.nextDueAt == null || dueAt.isBefore(accumulator.nextDueAt))) {
                accumulator.nextDueAt = dueAt;
            }
        }
    }

    private AgencyOpsResponse.QueueItem toQueueItem(PostCollectionEntity collection,
                                                    WorkspaceScope scope,
                                                    Set<String> eligibleApproverUserIds,
                                                    String attentionStatus) {
        List<String> platforms = collection.getPosts() == null
                ? List.of()
                : collection.getPosts().stream()
                .map(PostEntity::getProvider)
                .filter(Objects::nonNull)
                .map(Enum::name)
                .distinct()
                .sorted()
                .toList();

        int channelCount = collection.getPosts() != null ? collection.getPosts().size() : 0;

        return new AgencyOpsResponse.QueueItem(
                collection.getId(),
                scope.workspaceId(),
                scope.workspaceName(),
                scope.companyName(),
                collection.getDescription(),
                collection.getPostCollectionType() != null ? collection.getPostCollectionType().name() : null,
                collection.getReviewStatus() != null ? collection.getReviewStatus().name() : null,
                attentionStatus,
                collection.getNextApprovalStage() != null ? collection.getNextApprovalStage().name() : null,
                collection.getRequiredApprovalSteps(),
                collection.getCompletedApprovalSteps(),
                channelCount,
                platforms,
                eligibleApproverUserIds.stream().sorted().toList(),
                collection.getScheduledTime(),
                collection.getReviewSubmittedAt(),
                collection.getNextApprovalReminderAt(),
                collection.getApprovalEscalatedAt()
        );
    }

    private boolean isApprovalRisk(PostCollectionEntity collection, OffsetDateTime now) {
        return collection.getScheduledTime() != null
                && !collection.getScheduledTime().isAfter(now.plusHours(APPROVAL_RISK_WINDOW_HOURS));
    }

    private boolean isScheduledRisk(PostCollectionEntity collection) {
        return collection.getFailureState() == RecoveryState.RECOVERY_REQUIRED
                || collection.getFailureState() == RecoveryState.ESCALATED_TO_ADMIN;
    }

    private AgencyOpsResponse.PublishRiskItem toApprovalRiskItem(PostCollectionEntity collection,
                                                                 WorkspaceScope scope,
                                                                 String attentionStatus,
                                                                 Set<String> eligibleApproverUserIds,
                                                                 OffsetDateTime now) {
        String severity = riskSeverity(collection.getScheduledTime(), now, "ESCALATED".equals(attentionStatus));
        String reason = "Still waiting on approval before the scheduled publish window.";
        if ("ESCALATED".equals(attentionStatus)) {
            reason = "Approval reminders have already escalated and the publish window is approaching.";
        } else if ("OVERDUE".equals(attentionStatus)) {
            reason = "Approval reminder timing is overdue and the publish window is approaching.";
        }

        return new AgencyOpsResponse.PublishRiskItem(
                collection.getId(),
                scope.workspaceId(),
                scope.workspaceName(),
                scope.companyName(),
                collection.getDescription(),
                collection.getPostCollectionType() != null ? collection.getPostCollectionType().name() : null,
                "APPROVAL_PENDING",
                severity,
                reason,
                eligibleApproverUserIds.stream().sorted().toList(),
                collection.getScheduledTime(),
                collection.getReviewSubmittedAt(),
                collection.getApprovalEscalatedAt()
        );
    }

    private AgencyOpsResponse.PublishRiskItem toChangesRequestedRiskItem(PostCollectionEntity collection,
                                                                         WorkspaceScope scope,
                                                                         Set<String> eligibleApproverUserIds,
                                                                         OffsetDateTime now) {
        return new AgencyOpsResponse.PublishRiskItem(
                collection.getId(),
                scope.workspaceId(),
                scope.workspaceName(),
                scope.companyName(),
                collection.getDescription(),
                collection.getPostCollectionType() != null ? collection.getPostCollectionType().name() : null,
                "CHANGES_REQUESTED",
                riskSeverity(collection.getScheduledTime(), now, false),
                "Requested edits are still unresolved inside the publish window.",
                eligibleApproverUserIds.stream().sorted().toList(),
                collection.getScheduledTime(),
                collection.getReviewSubmittedAt(),
                collection.getApprovalEscalatedAt()
        );
    }

    private AgencyOpsResponse.PublishRiskItem toScheduledRiskItem(PostCollectionEntity collection,
                                                                  WorkspaceScope scope) {
        boolean escalated = collection.getFailureState() == RecoveryState.ESCALATED_TO_ADMIN;
        return new AgencyOpsResponse.PublishRiskItem(
                collection.getId(),
                scope.workspaceId(),
                scope.workspaceName(),
                scope.companyName(),
                collection.getDescription(),
                collection.getPostCollectionType() != null ? collection.getPostCollectionType().name() : null,
                escalated ? "PUBLISH_ESCALATED" : "RECOVERY_REQUIRED",
                escalated ? "HIGH" : "MEDIUM",
                escalated
                        ? "A scheduled publish issue has already been escalated to admin attention."
                        : "A scheduled publish needs recovery before client delivery is missed.",
                List.of(),
                collection.getScheduledTime(),
                collection.getReviewSubmittedAt(),
                collection.getApprovalEscalatedAt()
        );
    }

    private String resolveAttentionStatus(PostCollectionEntity collection, OffsetDateTime now) {
        if (collection.getApprovalEscalatedAt() != null) {
            return "ESCALATED";
        }
        if (collection.getNextApprovalReminderAt() != null && !collection.getNextApprovalReminderAt().isAfter(now)) {
            return "OVERDUE";
        }
        return "PENDING";
    }

    private String resolveHealthStatus(WorkspaceHealthAccumulator accumulator) {
        if (accumulator.escalatedApprovalCount > 0 || accumulator.atRiskPublishCount > 1) {
            return "CRITICAL";
        }
        if (accumulator.overdueApprovalCount > 0
                || accumulator.atRiskPublishCount > 0
                || accumulator.changesRequestedCount > 0
                || accumulator.pendingApprovalCount >= 5) {
            return "WATCH";
        }
        return "STABLE";
    }

    private String riskSeverity(OffsetDateTime scheduledTime, OffsetDateTime now, boolean escalated) {
        if (escalated || scheduledTime == null) {
            return "HIGH";
        }
        if (!scheduledTime.isAfter(now.plusHours(24))) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private Comparator<AgencyOpsResponse.QueueItem> queueComparator() {
        return Comparator
                .comparingInt((AgencyOpsResponse.QueueItem item) -> attentionRank(item.getAttentionStatus()))
                .thenComparing(item -> firstNonNull(item.getScheduledTime(), item.getReviewSubmittedAt()))
                .thenComparing(item -> safeLower(item.getWorkspaceName()));
    }

    private Comparator<AgencyOpsResponse.PublishRiskItem> publishRiskComparator() {
        return Comparator
                .comparingInt((AgencyOpsResponse.PublishRiskItem item) -> severityRank(item.getSeverity()))
                .thenComparing(item -> firstNonNull(item.getScheduledTime(), item.getReviewSubmittedAt()))
                .thenComparing(item -> safeLower(item.getWorkspaceName()));
    }

    private int attentionRank(String attentionStatus) {
        return switch (attentionStatus) {
            case "ESCALATED" -> 0;
            case "OVERDUE" -> 1;
            default -> 2;
        };
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private static int healthRank(String healthStatus) {
        return switch (healthStatus) {
            case "CRITICAL" -> 0;
            case "WATCH" -> 1;
            default -> 2;
        };
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private AgencyOpsResponse emptyResponse() {
        return new AgencyOpsResponse(
                new AgencyOpsResponse.Summary(0, 0, 0, 0, 0, 0),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private record WorkspaceScope(String workspaceId, String workspaceName, String companyName) {
    }

    private record ProfileSnapshot(String displayName, String email) {
    }

    private static final class WorkloadAccumulator {
        private final String userId;
        private final String displayName;
        private final String email;
        private final int workspaceCount;
        private int pendingApprovalCount;
        private int overdueApprovalCount;
        private int escalatedApprovalCount;
        private OffsetDateTime nextDueAt;

        private WorkloadAccumulator(String userId,
                                    String displayName,
                                    String email,
                                    int workspaceCount) {
            this.userId = userId;
            this.displayName = displayName;
            this.email = email;
            this.workspaceCount = workspaceCount;
        }

        private AgencyOpsResponse.ApproverWorkload toResponse() {
            return new AgencyOpsResponse.ApproverWorkload(
                    userId,
                    displayName,
                    email,
                    workspaceCount,
                    pendingApprovalCount,
                    overdueApprovalCount,
                    escalatedApprovalCount,
                    nextDueAt
            );
        }
    }

    private static final class WorkspaceHealthAccumulator {
        private final String workspaceId;
        private final String workspaceName;
        private final String companyName;
        private int pendingApprovalCount;
        private int overdueApprovalCount;
        private int escalatedApprovalCount;
        private int changesRequestedCount;
        private int atRiskPublishCount;
        private String healthStatus = "STABLE";

        private WorkspaceHealthAccumulator(WorkspaceScope scope) {
            this.workspaceId = scope.workspaceId();
            this.workspaceName = scope.workspaceName();
            this.companyName = scope.companyName();
        }

        private AgencyOpsResponse.WorkspaceHealth toResponse() {
            return new AgencyOpsResponse.WorkspaceHealth(
                    workspaceId,
                    workspaceName,
                    companyName,
                    pendingApprovalCount,
                    overdueApprovalCount,
                    escalatedApprovalCount,
                    changesRequestedCount,
                    atRiskPublishCount,
                    healthStatus
            );
        }
    }
}
