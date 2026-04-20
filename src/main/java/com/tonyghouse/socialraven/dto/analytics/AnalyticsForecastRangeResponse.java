package com.tonyghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsForecastRangeResponse {
    private Double lowValue;
    private Double expectedValue;
    private Double highValue;
}
