package com.tonyghouse.socialraven.dto.workspace;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgencyOpsResponse {
    private Summary summary;
    private List<WorkspaceOption> workspaces;
    private List<ApproverOption> approvers;
    private List<QueueItem> queue;
    private List<QueueItem> overdueQueue;
    private List<ApproverWorkload> workload;
    private List<WorkspaceHealth> workspaceHealth;
    private List<PublishRiskItem> publishRisk;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int workspaceCount;
        private int pendingApprovalCount;
        private int overdueApprovalCount;
        private int escalatedApprovalCount;
        private int atRiskPublishCount;
        private int approverCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceOption {
        private String workspaceId;
        private String workspaceName;
        private String companyName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApproverOption {
        private String userId;
        private String displayName;
        private String email;
        private int workspaceCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueItem {
        private Long collectionId;
        private String workspaceId;
        private String workspaceName;
        private String companyName;
        private String description;
        private String postCollectionType;
        private String reviewStatus;
        private String attentionStatus;
        private String nextApprovalStage;
        private Integer requiredApprovalSteps;
        private Integer completedApprovalSteps;
        private Integer channelCount;
        private List<String> platforms;
        private List<String> eligibleApproverUserIds;
        private OffsetDateTime scheduledTime;
        private OffsetDateTime reviewSubmittedAt;
        private OffsetDateTime nextApprovalReminderAt;
        private OffsetDateTime approvalEscalatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApproverWorkload {
        private String userId;
        private String displayName;
        private String email;
        private int workspaceCount;
        private int pendingApprovalCount;
        private int overdueApprovalCount;
        private int escalatedApprovalCount;
        private OffsetDateTime nextDueAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceHealth {
        private String workspaceId;
        private String workspaceName;
        private String companyName;
        private int pendingApprovalCount;
        private int overdueApprovalCount;
        private int escalatedApprovalCount;
        private int changesRequestedCount;
        private int atRiskPublishCount;
        private String healthStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishRiskItem {
        private Long collectionId;
        private String workspaceId;
        private String workspaceName;
        private String companyName;
        private String description;
        private String postCollectionType;
        private String riskType;
        private String severity;
        private String reason;
        private List<String> eligibleApproverUserIds;
        private OffsetDateTime scheduledTime;
        private OffsetDateTime reviewSubmittedAt;
        private OffsetDateTime approvalEscalatedAt;
    }
}
