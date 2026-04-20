package com.tonyghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsForecastPredictionResponse {
    private boolean available;
    private String confidenceTier;
    private long comparablePosts;
    private AnalyticsForecastRangeResponse range;
    private String basisSummary;
    private String unavailableReason;
}
