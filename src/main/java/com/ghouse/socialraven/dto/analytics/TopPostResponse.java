package com.ghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopPostResponse {
    private Long postId;
    private String provider;
    private String providerPostId;
    private String content;
    private OffsetDateTime publishedAt;
    private String snapshotType;
    private long impressions;
    private long reach;
    private long likes;
    private long comments;
    private long shares;
    private double engagementRate;
}
