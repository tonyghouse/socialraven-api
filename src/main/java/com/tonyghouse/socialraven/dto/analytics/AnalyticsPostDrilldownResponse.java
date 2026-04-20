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
public class AnalyticsPostDrilldownResponse {
    private String currentRangeLabel;
    private OffsetDateTime currentStartAt;
    private OffsetDateTime currentEndAt;
    private String metric;
    private String metricLabel;
    private String metricFormat;
    private AnalyticsPostRowResponse post;
    private AnalyticsComparableBenchmarkResponse comparableBenchmark;
    private AnalyticsPercentileRankResponse percentileRank;
    private List<AnalyticsPostMilestonePointResponse> milestoneProgression = new ArrayList<>();
    private List<AnalyticsPostRowResponse> comparablePosts = new ArrayList<>();
}
