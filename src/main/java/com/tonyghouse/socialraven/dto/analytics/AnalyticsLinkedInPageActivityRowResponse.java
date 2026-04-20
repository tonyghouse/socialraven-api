package com.tonyghouse.socialraven.dto.analytics;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsLinkedInPageActivityRowResponse {
    private String providerUserId;
    private String accountName;
    private Long followers;
    private Long followerDelta;
    private Long pageViews;
    private Long uniquePageViews;
    private Long clicks;
    private Double pageViewSharePercent;
    private Double clickSharePercent;
    private LocalDate lastSnapshotDate;
}
