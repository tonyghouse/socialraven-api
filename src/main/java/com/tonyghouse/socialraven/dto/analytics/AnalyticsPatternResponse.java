package com.tonyghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsPatternResponse {
    private String patternType;
    private String dimension;
    private String key;
    private String label;
    private long sampleSize;
    private double baselineValue;
    private double observedValue;
    private double liftPercent;
    private String confidenceTier;
    private String evidenceSummary;
}
