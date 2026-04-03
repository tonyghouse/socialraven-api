package com.tonyghouse.socialraven.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostCollectionVersionResponse {
    private Long id;
    private Integer versionNumber;
    private String versionEvent;
    private String actorType;
    private String actorUserId;
    private String actorDisplayName;
    private OffsetDateTime createdAt;
    private String reviewStatus;
    private boolean draft;
    private OffsetDateTime scheduledTime;
}
