package com.tonyghouse.socialraven.dto.analytics;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsRecommendationResponse {
    private Long id;
    private String recommendationKey;
    private String sourceType;
    private String contextLabel;
    private String title;
    private String actionSummary;
    private String evidenceSummary;
    private String confidenceTier;
    private String priority;
    private Double expectedImpactScore;
    private OffsetDateTime timeWindowStartAt;
    private OffsetDateTime timeWindowEndAt;
    private boolean dismissible;
}
