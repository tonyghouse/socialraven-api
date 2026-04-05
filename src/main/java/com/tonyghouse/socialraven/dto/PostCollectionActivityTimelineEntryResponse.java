package com.tonyghouse.socialraven.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostCollectionActivityTimelineEntryResponse {
    private String eventKey;
    private String category;
    private String eventType;
    private String label;
    private String actorType;
    private String actorUserId;
    private String actorDisplayName;
    private OffsetDateTime createdAt;
    private String note;
    private String fromStatus;
    private String toStatus;
    private Integer versionNumber;
    private OffsetDateTime scheduledTime;
}
