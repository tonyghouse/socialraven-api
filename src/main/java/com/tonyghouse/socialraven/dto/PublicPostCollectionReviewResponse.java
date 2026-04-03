package com.tonyghouse.socialraven.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicPostCollectionReviewResponse {
    private Long collectionId;
    private String description;
    private OffsetDateTime scheduledTime;
    private String postCollectionType;
    private String overallStatus;
    private String reviewStatus;
    private String nextApprovalStage;
    private List<PublicReviewChannelResponse> channels;
    private List<MediaResponse> media;
    private Map<String, Object> platformConfigs;
    private List<PostCollaborationThreadResponse> collaborationThreads;
    private OffsetDateTime linkExpiresAt;
    private boolean linkExpired;
    private boolean linkRevoked;
    private boolean canComment;
    private boolean canApprove;
    private boolean canReject;
}
