package com.tonyghouse.socialraven.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostCollectionReviewHistoryResponse {
    private Long id;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String actorType;
    private String actorUserId;
    private String actorDisplayName;
    private String note;
    private OffsetDateTime createdAt;
}
