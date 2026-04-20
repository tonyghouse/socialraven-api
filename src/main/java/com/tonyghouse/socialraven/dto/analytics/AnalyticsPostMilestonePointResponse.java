package com.tonyghouse.socialraven.dto.analytics;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsPostMilestonePointResponse {
    private OffsetDateTime fetchedAt;
    private Long impressions;
    private Long reach;
    private Long likes;
    private Long comments;
    private Long shares;
    private Long saves;
    private Long clicks;
    private Long videoViews;
    private Long watchTimeMinutes;
    private Long engagements;
    private Double engagementRate;
}
