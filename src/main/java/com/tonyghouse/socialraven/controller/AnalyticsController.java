package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.dto.analytics.AnalyticsBreakdownResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsManualRefreshResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsLinkedInPageActivityResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostRankingsResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostTableResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsShellResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTikTokCreatorActivityResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTrendExplorerResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsYouTubeChannelActivityResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsWorkspaceOverviewResponse;
import com.tonyghouse.socialraven.dto.analytics.HeatmapCellResponse;
import com.tonyghouse.socialraven.dto.analytics.PlatformStatsResponse;
import com.tonyghouse.socialraven.dto.analytics.TimelinePointResponse;
import com.tonyghouse.socialraven.dto.analytics.TopPostResponse;
import com.tonyghouse.socialraven.service.analytics.AnalyticsApiService;
import com.tonyghouse.socialraven.service.analytics.AnalyticsBackboneService;
import com.tonyghouse.socialraven.service.analytics.AnalyticsWorkspaceService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsApiService analyticsApiService;

    @Autowired
    private AnalyticsBackboneService analyticsBackboneService;

    @Autowired
    private AnalyticsWorkspaceService analyticsWorkspaceService;

    @GetMapping("/shell")
    public AnalyticsShellResponse getShell(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String providerUserId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String contentType) {
        return analyticsBackboneService.getShell(days, platform, providerUserId, campaignId, contentType);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AnalyticsManualRefreshResponse> requestRefresh(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String providerUserId) {
        return ResponseEntity.accepted().body(
                analyticsBackboneService.requestManualRefresh(platform, providerUserId)
        );
    }

    @GetMapping("/workspace-overview")
    public AnalyticsWorkspaceOverviewResponse getWorkspaceOverview(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String providerUserId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String contentType) {
        return analyticsWorkspaceService.getOverview(days, platform, providerUserId, campaignId, contentType);
    }

    @GetMapping("/post-table")
    public AnalyticsPostTableResponse getPostTable(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String providerUserId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String contentType,
            @RequestParam(defaultValue = "publishedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return analyticsWorkspaceService.getPostTable(
                days,
                platform,
                providerUserId,
                campaignId,
                contentType,
                sortBy,
                sortDirection,
                page,
                size
        );
    }

    @GetMapping("/post-rankings")
    public AnalyticsPostRankingsResponse getPostRankings(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String providerUserId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String contentType,
            @RequestParam(defaultValue = "engagements") String metric,
            @RequestParam(defaultValue = "5") int limit) {
        return analyticsWorkspaceService.getPostRankings(
                days,
                platform,
                providerUserId,
                campaignId,
                contentType,
                metric,
                limit
        );
    }

    @GetMapping("/trend-explorer")
    public AnalyticsTrendExplorerResponse getTrendExplorer(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String providerUserId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String contentType,
            @RequestParam(defaultValue = "engagements") String metric) {
        return analyticsWorkspaceService.getTrendExplorer(
                days,
                platform,
                providerUserId,
                campaignId,
                contentType,
                metric
        );
    }

    @GetMapping("/breakdown-engine")
    public AnalyticsBreakdownResponse getBreakdownEngine(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String providerUserId,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String contentType,
            @RequestParam(defaultValue = "platform") String dimension,
            @RequestParam(defaultValue = "engagements") String metric) {
        return analyticsWorkspaceService.getBreakdownEngine(
                days,
                platform,
                providerUserId,
                campaignId,
                contentType,
                dimension,
                metric
        );
    }

    @GetMapping("/linkedin-page-activity")
    public AnalyticsLinkedInPageActivityResponse getLinkedInPageActivity(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String providerUserId) {
        return analyticsWorkspaceService.getLinkedInPageActivity(days, platform, providerUserId);
    }

    @GetMapping("/youtube-channel-activity")
    public AnalyticsYouTubeChannelActivityResponse getYouTubeChannelActivity(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String providerUserId) {
        return analyticsWorkspaceService.getYouTubeChannelActivity(days, platform, providerUserId);
    }

    @GetMapping("/tiktok-creator-activity")
    public AnalyticsTikTokCreatorActivityResponse getTikTokCreatorActivity(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String providerUserId) {
        return analyticsWorkspaceService.getTikTokCreatorActivity(days, platform, providerUserId);
    }

    @GetMapping("/overview")
    public AnalyticsOverviewResponse getOverview(@RequestParam(defaultValue = "30") int days) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return analyticsApiService.getOverview(userId, days);
    }

    @GetMapping("/platforms")
    public List<PlatformStatsResponse> getPlatformStats(@RequestParam(defaultValue = "30") int days) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return analyticsApiService.getPlatformStats(userId, days);
    }

    @GetMapping("/posts")
    public List<TopPostResponse> getTopPosts(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "T30D") String snapshotType) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return analyticsApiService.getTopPosts(userId, days, snapshotType);
    }

    @GetMapping("/timeline")
    public List<TimelinePointResponse> getTimeline(@RequestParam(defaultValue = "30") int days) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return analyticsApiService.getTimeline(userId, days);
    }

    @GetMapping("/heatmap")
    public List<HeatmapCellResponse> getHeatmap() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return analyticsApiService.getHeatmap(userId);
    }
}
