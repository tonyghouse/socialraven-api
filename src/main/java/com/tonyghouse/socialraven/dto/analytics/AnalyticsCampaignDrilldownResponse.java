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
public class AnalyticsCampaignDrilldownResponse {
    private String currentRangeLabel;
    private OffsetDateTime currentStartAt;
    private OffsetDateTime currentEndAt;
    private String metric;
    private String metricLabel;
    private String metricFormat;
    private Long campaignId;
    private String campaignLabel;
    private AnalyticsComparableBenchmarkResponse comparableBenchmark;
    private AnalyticsPercentileRankResponse percentileRank;
    private AnalyticsDrilldownSummaryResponse summary;
    private List<AnalyticsTrendExplorerPointResponse> trend = new ArrayList<>();
    private AnalyticsDrilldownContributionResponse platformBreakdown;
    private AnalyticsDrilldownContributionResponse accountBreakdown;
    private List<AnalyticsPostRowResponse> topPosts = new ArrayList<>();
}
