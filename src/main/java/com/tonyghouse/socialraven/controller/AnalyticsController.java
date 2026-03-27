package com.tonyghouse.socialraven.controller;

import com.ghouse.socialraven.dto.analytics.*;
import com.tonyghouse.socialraven.dto.analytics.*;
import com.tonyghouse.socialraven.service.analytics.AnalyticsApiService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsApiService analyticsApiService;

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
