package com.tonyghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsEndOfPeriodForecastResponse {
    private boolean available;
    private int forecastDays;
    private String planningWindowLabel;
    private int plannedPosts;
    private Double historicalPostsPerDay;
    private String confidenceTier;
    private long comparablePosts;
    private AnalyticsForecastRangeResponse range;
    private String basisSummary;
    private String unavailableReason;
}
