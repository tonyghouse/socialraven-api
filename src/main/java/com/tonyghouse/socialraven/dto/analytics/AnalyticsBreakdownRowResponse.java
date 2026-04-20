package com.tonyghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsBreakdownRowResponse {
    private String key;
    private String label;
    private long postsPublished;
    private double performanceValue;
    private Double outputSharePercent;
    private Double performanceSharePercent;
    private Double shareGapPercent;
    private Double averagePerformancePerPost;
}
