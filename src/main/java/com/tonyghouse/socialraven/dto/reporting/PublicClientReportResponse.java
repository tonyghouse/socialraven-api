package com.tonyghouse.socialraven.dto.reporting;

import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewResponse;
import com.tonyghouse.socialraven.dto.analytics.PlatformStatsResponse;
import com.tonyghouse.socialraven.dto.analytics.TimelinePointResponse;
import com.tonyghouse.socialraven.dto.analytics.TopPostResponse;
import java.time.OffsetDateTime;
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
    private String commentary;
    private List<String> highlights;
    private OffsetDateTime generatedAt;
    private OffsetDateTime linkExpiresAt;
    private AnalyticsOverviewResponse overview;
    private List<PlatformStatsResponse> platformStats;
    private List<TopPostResponse> topPosts;
    private List<TimelinePointResponse> timeline;
}
