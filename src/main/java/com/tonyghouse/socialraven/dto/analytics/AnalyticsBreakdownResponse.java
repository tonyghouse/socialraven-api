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
public class AnalyticsBreakdownResponse {
    private String currentRangeLabel;
    private OffsetDateTime currentStartAt;
    private OffsetDateTime currentEndAt;
    private String dimension;
    private String dimensionLabel;
    private String metric;
    private String metricLabel;
    private String metricFormat;
    private long totalPostsPublished;
    private double totalPerformanceValue;
    private Double averagePerformancePerPost;
    private List<AnalyticsBreakdownRowResponse> rows = new ArrayList<>();
}
