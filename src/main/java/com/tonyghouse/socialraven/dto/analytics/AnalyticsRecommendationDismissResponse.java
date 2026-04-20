package com.tonyghouse.socialraven.dto.analytics;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsRecommendationDismissResponse {
    private Long recommendationId;
    private OffsetDateTime dismissedAt;
}
