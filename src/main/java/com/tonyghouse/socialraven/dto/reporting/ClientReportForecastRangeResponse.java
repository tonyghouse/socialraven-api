package com.tonyghouse.socialraven.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportForecastRangeResponse {
    private Double lowValue;
    private Double expectedValue;
    private Double highValue;
}
