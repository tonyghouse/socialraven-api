package com.tonyghouse.socialraven.dto;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class CreatePostCollectionReviewLinkRequest {
    private OffsetDateTime expiresAt;
}
