package com.tonyghouse.socialraven.dto;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostCollaborationReplyResponse {
    private Long id;
    private String authorUserId;
    private String authorDisplayName;
    private String body;
    private List<PostCollaborationMentionResponse> mentions;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
