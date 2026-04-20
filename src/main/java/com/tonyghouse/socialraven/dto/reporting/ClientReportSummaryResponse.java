package com.tonyghouse.socialraven.dto.reporting;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportSummaryResponse {
    private String currentRangeLabel;
    private OffsetDateTime currentStartAt;
    private OffsetDateTime currentEndAt;
    private long impressions;
    private long engagements;
    private double engagementRate;
    private long clicks;
    private long videoViews;
    private long postsPublished;
}
