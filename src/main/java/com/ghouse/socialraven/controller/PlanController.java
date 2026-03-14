package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.plan.ChangePlanRequest;
import com.ghouse.socialraven.dto.plan.UsageStatsResponse;
import com.ghouse.socialraven.dto.plan.UserPlanResponse;
import com.ghouse.socialraven.service.plan.UsageService;
import com.ghouse.socialraven.service.plan.UserPlanService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/plans")
public class PlanController {

    @Autowired
    private UserPlanService userPlanService;

    @Autowired
    private UsageService usageService;

    /**
     * GET /plans/me
     * Returns the authenticated user's current plan and limits.
     * Auto-creates a 14-day TRIAL plan on first access.
     */
    @GetMapping("/me")
    public UserPlanResponse getUserPlan() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return userPlanService.getUserPlan(userId);
    }

    /**
     * PATCH /plans/me
     * Body: { "planType": "PRO" }
     * Switches the authenticated user to the requested plan.
     */
    @PatchMapping("/me")
    public UserPlanResponse changePlan(@RequestBody ChangePlanRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return userPlanService.changePlan(userId, request);
    }

    /**
     * GET /plans/usage
     * Returns posts-used-this-month and connected-accounts counts vs. plan limits.
     */
    @GetMapping("/usage")
    public UsageStatsResponse getUsage() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return usageService.getUsageStats(userId);
    }
}
