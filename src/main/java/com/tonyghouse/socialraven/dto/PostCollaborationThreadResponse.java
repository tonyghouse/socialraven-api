package com.tonyghouse.socialraven.dto;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostCollaborationThreadResponse {
    private Long id;
    private String threadType;
    private String visibility;
    private String status;
    private String authorType;
    private String authorUserId;
    private String authorDisplayName;
    private String body;
    private List<PostCollaborationMentionResponse> mentions;
    private Integer anchorStart;
    private Integer anchorEnd;
    private String anchorText;
    private Long mediaId;
    private Double mediaMarkerX;
    private Double mediaMarkerY;
    private String suggestedText;
    private String suggestionStatus;
    private String suggestionDecidedByUserId;
    private String suggestionDecidedByDisplayName;
    private OffsetDateTime suggestionDecidedAt;
    private String resolvedByUserId;
    private String resolvedByDisplayName;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<PostCollaborationReplyResponse> replies;
}
