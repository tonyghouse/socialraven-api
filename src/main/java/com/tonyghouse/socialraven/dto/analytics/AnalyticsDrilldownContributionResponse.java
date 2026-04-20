package com.tonyghouse.socialraven.dto.analytics;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsDrilldownContributionResponse {
    private String dimension;
    private String dimensionLabel;
    private List<AnalyticsBreakdownRowResponse> rows = new ArrayList<>();
}
