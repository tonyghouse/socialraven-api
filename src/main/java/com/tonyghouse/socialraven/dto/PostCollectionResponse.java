package com.tonyghouse.socialraven.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostCollectionResponse {
    private Long id;
    private String description;
    private OffsetDateTime scheduledTime;
    private String postCollectionType;
    private String overallStatus;
    private String reviewStatus;
    private OffsetDateTime reviewSubmittedAt;
    private OffsetDateTime approvedAt;
    private boolean approvalLocked;
    private OffsetDateTime approvalLockedAt;
    private Integer requiredApprovalSteps;
    private Integer completedApprovalSteps;
    private String nextApprovalStage;
    private List<PostResponse> posts;
    private List<MediaResponse> media;
    private List<PostCollectionReviewHistoryResponse> reviewHistory;
    private Integer approvalReminderAttemptCount;
    private OffsetDateTime lastApprovalReminderSentAt;
    private OffsetDateTime nextApprovalReminderAt;
    private OffsetDateTime approvalEscalatedAt;
    private List<PostCollectionVersionResponse> versionHistory;
    private PostCollectionApprovalDiffResponse approvedDiff;
    /** Platform-specific configuration, e.g. YouTube title, Instagram alt text. */
    private Map<String, Object> platformConfigs;
    private String failureState;
    private String failureReasonSummary;
    private Integer recoveryNotificationAttemptCount;
    private Long recoveryCollectionId;
    private Long recoverySourceCollectionId;
    private boolean recoveryRequired;
    private boolean recoveryHandled;
    private Integer publishedChannelCount;
    private Integer failedChannelCount;
}
