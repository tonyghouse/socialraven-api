package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.plan.ChangePlanRequest;
import com.tonyghouse.socialraven.dto.plan.UsageStatsResponse;
import com.tonyghouse.socialraven.dto.plan.UserPlanResponse;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.service.plan.UsageService;
import com.tonyghouse.socialraven.service.plan.UserPlanService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
     * Returns the workspace plan when X-Workspace-Id header is present;
     * falls back to the authenticated user's personal plan otherwise.
     */
    @GetMapping("/me")
    public UserPlanResponse getUserPlan() {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        if (workspaceId != null) {
            return userPlanService.getWorkspacePlan(workspaceId);
        }
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return userPlanService.getUserPlan(userId);
    }

    /**
     * PATCH /plans/me
     * Body: { "planType": "PRO" }
     * Changes the workspace plan (OWNER only) or the personal plan.
     */
    @PatchMapping("/me")
    public UserPlanResponse changePlan(@RequestBody ChangePlanRequest request) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        if (workspaceId != null) {
            WorkspaceRole role = WorkspaceContext.getRole();
            if (role == null || !role.isAtLeast(WorkspaceRole.OWNER)) {
                throw new SocialRavenException("Only the workspace owner can change the plan", HttpStatus.FORBIDDEN);
            }
            return userPlanService.changeWorkspacePlan(workspaceId, request);
        }
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return userPlanService.changePlan(userId, request);
    }

    /**
     * GET /plans/usage
     * Returns general usage plus workspace-scoped x.com usage and limits.
     */
    @GetMapping("/usage")
    public UsageStatsResponse getUsage() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return usageService.getUsageStats(userId);
    }
}
