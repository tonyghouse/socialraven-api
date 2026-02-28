package com.ghouse.socialraven.dto;

import java.time.OffsetDateTime;

public record CalendarPostResponse(
        Long id,
        Long postCollectionId,
        String title,
        String platform,
        String providerUserId,
        String postStatus,
        String postCollectionType,
        OffsetDateTime scheduledTime) {
}
