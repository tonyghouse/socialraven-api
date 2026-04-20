package com.tonyghouse.socialraven.dto.analytics;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsShellResponse {
    private AnalyticsShellSummaryResponse summary = new AnalyticsShellSummaryResponse();
    private AnalyticsFilterOptionsResponse filters = new AnalyticsFilterOptionsResponse();
    private List<AnalyticsProviderCoverageResponse> coverage = new ArrayList<>();
}
