package com.tonyghouse.socialraven.dto.reporting;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportCampaignInsightResponse {
    private Long campaignId;
    private String campaignLabel;
    private long postsPublished;
    private double engagements;
    private Double averageEngagementsPerPost;
    private Double benchmarkAverage;
    private Double liftPercent;
    private Double percentile;
    private Integer rank;
    private long comparableCount;
    private List<ClientReportTrendPointResponse> trend = new ArrayList<>();
    private ClientReportContributionResponse platformBreakdown;
    private ClientReportContributionResponse accountBreakdown;
}
