package com.tonyghouse.socialraven.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportPlatformPerformanceResponse {
    private String provider;
    private String platformLabel;
    private long postsPublished;
    private long engagements;
    private long impressions;
    private Double averageEngagementsPerPost;
    private Double outputSharePercent;
    private Double engagementSharePercent;
    private Double impressionSharePercent;
}
