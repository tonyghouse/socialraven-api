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
public class AnalyticsYouTubeChannelActivityResponse {
    private String currentRangeLabel;
    private LocalDate currentStartDate;
    private LocalDate currentEndDate;
    private long trackedAccounts;
    private long totalVideoViews;
    private long totalLikes;
    private long totalComments;
    private long totalShares;
    private long totalWatchTimeMinutes;
    private long totalSubscriberDelta;
    private List<AnalyticsYouTubeChannelActivityTrendPointResponse> trend = new ArrayList<>();
    private List<AnalyticsYouTubeChannelActivityRowResponse> rows = new ArrayList<>();
}
