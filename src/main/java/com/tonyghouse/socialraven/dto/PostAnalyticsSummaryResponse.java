package com.tonyghouse.socialraven.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostAnalyticsSummaryResponse {
    private String freshnessStatus;
    private OffsetDateTime lastCollectedAt;
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
