package com.tonyghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsOverviewMetricResponse {
    private String key;
    private String label;
    private String format;
    private double currentValue;
    private double previousValue;
    private double deltaValue;
    private Double deltaPercent;
}
