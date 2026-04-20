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
public class AnalyticsTrendExplorerResponse {
    private String currentRangeLabel;
    private OffsetDateTime currentStartAt;
    private OffsetDateTime currentEndAt;
    private String metric;
    private String metricLabel;
    private String metricFormat;
    private double totalPerformanceValue;
    private long totalPostsPublished;
    private Double averagePerformancePerPost;
    private List<AnalyticsTrendExplorerPointResponse> daily = new ArrayList<>();
    private List<AnalyticsTrendExplorerPointResponse> weekly = new ArrayList<>();
}
