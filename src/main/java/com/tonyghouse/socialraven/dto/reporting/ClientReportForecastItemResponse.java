package com.tonyghouse.socialraven.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportForecastItemResponse {
    private boolean available;
    private String label;
    private String confidenceTier;
    private String slotLabel;
    private Integer forecastDays;
    private Integer plannedPosts;
    private long comparablePosts;
    private Double liftPercent;
    private ClientReportForecastRangeResponse range;
    private String basisSummary;
    private String unavailableReason;
}
