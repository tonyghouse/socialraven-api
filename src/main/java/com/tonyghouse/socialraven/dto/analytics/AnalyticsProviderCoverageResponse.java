package com.tonyghouse.socialraven.dto.analytics;

import com.tonyghouse.socialraven.model.AnalyticsMetricAvailabilityWindow;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsProviderCoverageResponse {
    private String provider;
    private int connectedAccountCount;
    private String postAnalyticsState;
    private String accountAnalyticsState;
    private String freshnessStatus;
    private long trackedPostCount;
    private long milestoneSnapshotCount;
    private long accountDailySnapshotCount;
    private OffsetDateTime lastPostAnalyticsAt;
    private OffsetDateTime lastAccountAnalyticsAt;
    private OffsetDateTime lastSuccessfulJobAt;
    private OffsetDateTime lastAttemptedJobAt;
    private OffsetDateTime lastManualRefreshRequestedAt;
    private List<AnalyticsMetricAvailabilityWindow> metricAvailability = new ArrayList<>();
    private List<String> supportedPostMetrics = new ArrayList<>();
    private List<String> supportedAccountMetrics = new ArrayList<>();
    private List<String> collectionModes = new ArrayList<>();
    private String lastErrorSummary;
}
