package com.tonyghouse.socialraven.dto.analytics;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsForecastBestSlotResponse {
    private boolean available;
    private String slotKey;
    private String slotLabel;
    private OffsetDateTime predictedAt;
    private String confidenceTier;
    private long comparablePosts;
    private Double baselineValue;
    private Double observedValue;
    private Double liftPercent;
    private AnalyticsForecastRangeResponse range;
    private String basisSummary;
    private String unavailableReason;
}
