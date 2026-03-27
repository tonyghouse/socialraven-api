package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.dto.onboarding.CompleteOnboardingRequest;
import com.tonyghouse.socialraven.dto.onboarding.OnboardingStatusResponse;
import com.tonyghouse.socialraven.service.onboarding.OnboardingService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/onboarding")
public class OnboardingController {

    @Autowired
    private OnboardingService onboardingService;

    /**
     * GET /onboarding/status
     * Returns whether the caller has completed onboarding.
     * Frontend checks this on every app load; if completed=false → redirect to /onboarding.
     */
    @GetMapping("/status")
    public OnboardingStatusResponse getStatus() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return onboardingService.getStatus(userId);
    }

    /**
     * POST /onboarding/complete
     * Body: { userType, workspaceName?, companyName? }
     * Creates user_profile + first workspace + OWNER membership.
     * Idempotent — safe to call multiple times.
     */
    @PostMapping("/complete")
    public OnboardingStatusResponse complete(@RequestBody CompleteOnboardingRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return onboardingService.completeOnboarding(userId, request);
    }

    /**
     * POST /onboarding/upgrade-to-agency
     * Switches user type from INFLUENCER to AGENCY.
     * Existing workspace is preserved; agency plan features unlock.
     */
    @PostMapping("/upgrade-to-agency")
    public OnboardingStatusResponse upgradeToAgency() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return onboardingService.upgradeToAgency(userId);
    }
}
