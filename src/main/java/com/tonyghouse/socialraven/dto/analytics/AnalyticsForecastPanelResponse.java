package com.tonyghouse.socialraven.dto.analytics;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsForecastPanelResponse {
    private String currentRangeLabel;
    private OffsetDateTime currentStartAt;
    private OffsetDateTime currentEndAt;
    private String metric;
    private String metricLabel;
    private String metricFormat;
    private int forecastDays;
    private String planningWindowLabel;
    private int plannedPosts;
    private int minimumComparablePosts;
    private int minimumSlotSampleSize;
    private long eligiblePostCount;
    private long excludedPostCount;
    private String basisNote;
    private AnalyticsForecastPredictionResponse nextPostForecast;
    private AnalyticsForecastBestSlotResponse nextBestSlot;
    private AnalyticsEndOfPeriodForecastResponse endOfPeriodForecast;
}
