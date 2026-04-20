package com.tonyghouse.socialraven.dto.analytics;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsManualRefreshProviderResultResponse {
    private String provider;
    private int scheduledJobs;
    private String status;
    private String reason;
    private OffsetDateTime nextAllowedAt;
}
