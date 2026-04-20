package com.tonyghouse.socialraven.dto.analytics;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsYouTubeChannelActivityRowResponse {
    private String providerUserId;
    private String accountName;
    private Long followers;
    private Long subscriberDelta;
    private Long videoViews;
    private Long likes;
    private Long comments;
    private Long shares;
    private Long watchTimeMinutes;
    private Double viewSharePercent;
    private Double watchTimeSharePercent;
    private LocalDate lastSnapshotDate;
}
