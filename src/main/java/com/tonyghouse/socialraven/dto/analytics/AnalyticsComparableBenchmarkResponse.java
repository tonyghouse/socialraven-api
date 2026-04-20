package com.tonyghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsComparableBenchmarkResponse {
    private String groupLabel;
    private String basisLabel;
    private long comparableCount;
    private Double targetValue;
    private Double comparableAverageValue;
    private Double sliceAverageValue;
    private Double liftPercent;
}
