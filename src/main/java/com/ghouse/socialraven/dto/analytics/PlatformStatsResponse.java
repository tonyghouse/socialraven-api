package com.ghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlatformStatsResponse {
    private String provider;
    private long impressions;
    private long reach;
    private long likes;
    private long comments;
    private long shares;
    private long clicks;
    private long videoViews;
    private long followerGrowth;
    private int postsPublished;
    private double engagementRate;
}
