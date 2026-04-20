package com.tonyghouse.socialraven.dto.analytics;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsPlatformDrilldownResponse {
    private String currentRangeLabel;
    private OffsetDateTime currentStartAt;
    private OffsetDateTime currentEndAt;
    private String metric;
    private String metricLabel;
    private String metricFormat;
    private String provider;
    private String platformLabel;
    private AnalyticsComparableBenchmarkResponse comparableBenchmark;
    private AnalyticsPercentileRankResponse percentileRank;
    private AnalyticsDrilldownSummaryResponse summary;
    private List<AnalyticsTrendExplorerPointResponse> trend = new ArrayList<>();
    private AnalyticsDrilldownContributionResponse accountBreakdown;
    private AnalyticsDrilldownContributionResponse postTypeBreakdown;
    private AnalyticsDrilldownContributionResponse mediaFormatBreakdown;
    private List<AnalyticsPostRowResponse> topPosts = new ArrayList<>();
}
