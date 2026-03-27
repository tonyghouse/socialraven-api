package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.dto.plan.AdminPlanOverrideRequest;
import com.tonyghouse.socialraven.dto.plan.UserPlanResponse;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.service.plan.UserPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only endpoints for manually managing user plans.
 *
 * Protected by the X-Admin-Key header (not by Clerk JWT).
 * Set ADMIN_API_KEY environment variable to a strong secret before deploying.
 *
 * Example curl:
 *   curl -X PUT https://api.socialraven.io/admin/plans/user_clerk_id \
 *        -H "X-Admin-Key: <secret>" \
 *        -H "Content-Type: application/json" \
 *        -d '{"planType":"AGENCY_PRO","status":"ACTIVE","customPostsLimit":10000,"customAccountsLimit":500}'
 */
@RestController
@RequestMapping("/admin/plans")
public class AdminPlanController {

    @Value("${socialraven.admin.api-key:}")
    private String adminApiKey;

    @Autowired
    private UserPlanService userPlanService;

    /**
     * GET /admin/plans/{userId}
     * Returns the current plan record for any user.
     */
    @GetMapping("/{userId}")
    public UserPlanResponse getUserPlan(
            @PathVariable String userId,
            @RequestHeader("X-Admin-Key") String providedKey) {
        checkAdminKey(providedKey);
        return userPlanService.getUserPlan(userId);
    }

    /**
     * PUT /admin/plans/{userId}
     * Overrides plan type, status, and/or custom limits for a specific user.
     * Pass customPostsLimit / customAccountsLimit = -1 to clear the override (revert to plan default).
     */
    @PutMapping("/{userId}")
    public UserPlanResponse overridePlan(
            @PathVariable String userId,
            @RequestHeader("X-Admin-Key") String providedKey,
            @RequestBody AdminPlanOverrideRequest request) {
        checkAdminKey(providedKey);
        return userPlanService.adminOverride(userId, request);
    }

    /**
     * GET /admin/plans/workspace/{workspaceId}
     * Returns the current plan record for the workspace owner.
     */
    @GetMapping("/workspace/{workspaceId}")
    public UserPlanResponse getWorkspacePlan(
            @PathVariable String workspaceId,
            @RequestHeader("X-Admin-Key") String providedKey) {
        checkAdminKey(providedKey);
        return userPlanService.getWorkspacePlan(workspaceId);
    }

    /**
     * PUT /admin/plans/workspace/{workspaceId}
     * Overrides plan type, status, and/or custom limits keyed by workspaceId.
     */
    @PutMapping("/workspace/{workspaceId}")
    public UserPlanResponse overrideWorkspacePlan(
            @PathVariable String workspaceId,
            @RequestHeader("X-Admin-Key") String providedKey,
            @RequestBody AdminPlanOverrideRequest request) {
        checkAdminKey(providedKey);
        return userPlanService.adminOverrideByWorkspace(workspaceId, request);
    }

    // ─── Helper ─────────────────────────────────────────────────────────────────

    private void checkAdminKey(String providedKey) {
        if (adminApiKey.isBlank()) {
            throw new SocialRavenException("Admin API not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!adminApiKey.equals(providedKey)) {
            throw new SocialRavenException("Forbidden", HttpStatus.FORBIDDEN);
        }
    }
}
