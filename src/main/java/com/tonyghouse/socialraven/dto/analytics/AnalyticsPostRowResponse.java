package com.tonyghouse.socialraven.dto.analytics;

import com.tonyghouse.socialraven.model.AnalyticsMetricAvailabilityWindow;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsPostRowResponse {
    private Long postId;
    private String provider;
    private String providerUserId;
    private String providerPostId;
    private String accountName;
    private Long campaignId;
    private String campaignLabel;
    private String content;
    private String postType;
    private String mediaFormat;
    private String freshnessStatus;
    private OffsetDateTime publishedAt;
    private OffsetDateTime lastCollectedAt;
    private boolean hasLiveMetrics;
    private List<AnalyticsMetricAvailabilityWindow> metricAvailability = new ArrayList<>();
    private Long impressions;
    private Long reach;
    private Long likes;
    private Long comments;
    private Long shares;
    private Long saves;
    private Long clicks;
    private Long videoViews;
    private Long watchTimeMinutes;
    private Long engagements;
    private Double engagementRate;
}
