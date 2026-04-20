package com.tonyghouse.socialraven.dto.analytics;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsYouTubeChannelActivityTrendPointResponse {
    private LocalDate snapshotDate;
    private Long videoViews;
    private Long watchTimeMinutes;
    private Long subscriberDelta;
}
