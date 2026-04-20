package com.tonyghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsPercentileRankResponse {
    private Double percentile;
    private Integer rank;
    private long comparableCount;
}
