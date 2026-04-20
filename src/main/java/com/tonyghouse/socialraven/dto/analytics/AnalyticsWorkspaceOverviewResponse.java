package com.tonyghouse.socialraven.dto.analytics;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsWorkspaceOverviewResponse {
    private String currentRangeLabel;
    private String previousRangeLabel;
    private OffsetDateTime currentStartAt;
    private OffsetDateTime currentEndAt;
    private OffsetDateTime previousStartAt;
    private OffsetDateTime previousEndAt;
    private List<AnalyticsOverviewMetricResponse> metrics = new ArrayList<>();
}
