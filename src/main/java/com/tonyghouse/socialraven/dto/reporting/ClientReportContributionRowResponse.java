package com.tonyghouse.socialraven.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportContributionRowResponse {
    private String key;
    private String label;
    private long postsPublished;
    private double performanceValue;
    private Double outputSharePercent;
    private Double performanceSharePercent;
    private Double shareGapPercent;
    private Double averagePerformancePerPost;
}
