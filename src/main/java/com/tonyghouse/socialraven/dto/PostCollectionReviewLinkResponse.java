package com.tonyghouse.socialraven.dto;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostCollectionReviewLinkResponse {
    private String id;
    private String token;
    private String createdByUserId;
    private String createdByDisplayName;
    private String shareScope;
    private List<Long> sharedPostIds;
    private boolean passcodeProtected;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime createdAt;
    private boolean active;
}
