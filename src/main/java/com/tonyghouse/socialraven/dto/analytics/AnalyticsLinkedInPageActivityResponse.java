package com.tonyghouse.socialraven.dto.analytics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsLinkedInPageActivityResponse {
    private String currentRangeLabel;
    private LocalDate currentStartDate;
    private LocalDate currentEndDate;
    private long trackedAccounts;
    private long totalPageViews;
    private long totalUniquePageViews;
    private long totalClicks;
    private long totalFollowerDelta;
    private List<AnalyticsLinkedInPageActivityRowResponse> rows = new ArrayList<>();
}
