package com.tonyghouse.socialraven.dto.analytics;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsShellSummaryResponse {
    private int connectedAccountCount;
    private int campaignCount;
    private int publishedPostCount;
    private long trackedPostCount;
    private long milestoneSnapshotCount;
    private long accountDailySnapshotCount;
    private int pendingJobCount;
    private OffsetDateTime lastAnalyticsAt;
    private boolean hasLiveData;
}
