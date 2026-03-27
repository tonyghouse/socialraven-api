package com.tonyghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsOverviewResponse {
    private long totalImpressions;
    private long totalReach;
    private long totalLikes;
    private long totalComments;
    private long totalShares;
    private long totalVideoViews;
    private long followerGrowth;
    private int totalPosts;
    private double avgEngagementRate;
}
