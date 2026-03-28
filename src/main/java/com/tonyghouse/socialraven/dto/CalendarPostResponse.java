package com.tonyghouse.socialraven.dto;

import java.time.OffsetDateTime;

public record CalendarPostResponse(
        Long id,
        Long postCollectionId,
        String platform,
        String providerUserId,
        String postStatus,
        String postCollectionType,
        OffsetDateTime scheduledTime) {
}
