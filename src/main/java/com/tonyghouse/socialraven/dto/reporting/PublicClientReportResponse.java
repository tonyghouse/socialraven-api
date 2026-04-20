package com.tonyghouse.socialraven.dto.reporting;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicClientReportResponse {
    private String reportTitle;
    private String clientLabel;
    private String agencyLabel;
    private String workspaceName;
    private String companyName;
    private String logoUrl;
    private String templateType;
    private Integer reportDays;
    private String reportWindowLabel;
    private String reportScope;
    private String reportScopeLabel;
    private Long campaignId;
    private String campaignLabel;
    private String commentary;
    private List<String> highlights = new ArrayList<>();
    private OffsetDateTime generatedAt;
    private OffsetDateTime linkExpiresAt;
    private ClientReportSummaryResponse summary;
    private List<ClientReportPlatformPerformanceResponse> platformPerformance = new ArrayList<>();
    private List<ClientReportTopPostResponse> topPosts = new ArrayList<>();
    private List<ClientReportTrendPointResponse> trend = new ArrayList<>();
    private ClientReportForecastSummaryResponse forecast;
    private ClientReportCampaignInsightResponse campaignInsight;
}
