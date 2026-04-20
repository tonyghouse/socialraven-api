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
public class AnalyticsAccountDrilldownResponse {
    private String currentRangeLabel;
    private OffsetDateTime currentStartAt;
    private OffsetDateTime currentEndAt;
    private String metric;
    private String metricLabel;
    private String metricFormat;
    private String provider;
    private String providerUserId;
    private String accountName;
    private AnalyticsComparableBenchmarkResponse comparableBenchmark;
    private AnalyticsPercentileRankResponse percentileRank;
    private AnalyticsDrilldownSummaryResponse summary;
    private List<AnalyticsTrendExplorerPointResponse> trend = new ArrayList<>();
    private AnalyticsDrilldownContributionResponse postTypeBreakdown;
    private AnalyticsDrilldownContributionResponse mediaFormatBreakdown;
    private List<AnalyticsPostRowResponse> topPosts = new ArrayList<>();
}
