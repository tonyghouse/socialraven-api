package com.ghouse.socialraven.service.plan;

import com.ghouse.socialraven.constant.PlanStatus;
import com.ghouse.socialraven.constant.PlanType;
import com.ghouse.socialraven.dto.plan.AdminPlanOverrideRequest;
import com.ghouse.socialraven.dto.plan.ChangePlanRequest;
import com.ghouse.socialraven.dto.plan.UserPlanResponse;
import com.ghouse.socialraven.entity.PlanConfigEntity;
import com.ghouse.socialraven.entity.UserPlanEntity;
import com.ghouse.socialraven.entity.WorkspaceEntity;
import com.ghouse.socialraven.exception.SocialRavenException;
import com.ghouse.socialraven.repo.PlanConfigRepo;
import com.ghouse.socialraven.repo.UserPlanRepo;
import com.ghouse.socialraven.repo.WorkspaceRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
public class UserPlanService {

    @Autowired
    private UserPlanRepo userPlanRepo;

    @Autowired
    private PlanConfigRepo planConfigRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    /**
     * Returns the user's plan, auto-creating a 14-day TRIAL on first access.
     */
    @Transactional
    public UserPlanResponse getUserPlan(String userId) {
        UserPlanEntity entity = getOrCreate(userId);
        return toResponse(entity);
    }

    /**
     * Changes the authenticated user's plan.
     * When Stripe is live, this will also create/update a Stripe subscription.
     */
    @Transactional
    public UserPlanResponse changePlan(String userId, ChangePlanRequest request) {
        PlanType newPlanType = request.getPlanType();
        if (newPlanType == null) {
            throw new SocialRavenException("planType is required", HttpStatus.BAD_REQUEST);
        }
        // Verify plan config exists
        planConfigRepo.findById(newPlanType)
                .orElseThrow(() -> new SocialRavenException("Unknown plan: " + newPlanType, HttpStatus.BAD_REQUEST));

        UserPlanEntity entity = getOrCreate(userId);

        if (entity.getPlanType() == newPlanType) {
            return toResponse(entity);
        }

        entity.setPlanType(newPlanType);

        if (newPlanType == PlanType.TRIAL) {
            // Downgrading back to trial is not normally allowed; treat as ACTIVE trial
            entity.setStatus(PlanStatus.TRIALING);
            OffsetDateTime trialEnd = OffsetDateTime.now().plusDays(14);
            entity.setTrialEndsAt(trialEnd);
            entity.setRenewalDate(trialEnd);
        } else {
            entity.setStatus(PlanStatus.ACTIVE);
            entity.setTrialEndsAt(null);
            entity.setRenewalDate(OffsetDateTime.now().plusDays(30));
        }

        entity.setCancelAtPeriodEnd(false);
        entity.setUpdatedAt(OffsetDateTime.now());
        // Clear any custom overrides when user voluntarily changes plan
        entity.setCustomPostsLimit(null);
        entity.setCustomAccountsLimit(null);

        userPlanRepo.save(entity);
        log.info("User {} changed plan to {}", userId, newPlanType);
        return toResponse(entity);
    }

    /**
     * Admin override — allows manually setting plan type, status, and custom limits
     * for any user (e.g. enterprise customers with negotiated terms).
     */
    @Transactional
    public UserPlanResponse adminOverride(String targetUserId, AdminPlanOverrideRequest request) {
        UserPlanEntity entity = getOrCreate(targetUserId);

        if (request.getPlanType() != null) {
            planConfigRepo.findById(request.getPlanType())
                    .orElseThrow(() -> new SocialRavenException("Unknown plan: " + request.getPlanType(), HttpStatus.BAD_REQUEST));
            entity.setPlanType(request.getPlanType());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (request.getCustomPostsLimit() != null) {
            entity.setCustomPostsLimit(request.getCustomPostsLimit() < 0 ? null : request.getCustomPostsLimit());
        }
        if (request.getCustomAccountsLimit() != null) {
            entity.setCustomAccountsLimit(request.getCustomAccountsLimit() < 0 ? null : request.getCustomAccountsLimit());
        }
        if (request.getRenewalDate() != null) {
            entity.setRenewalDate(OffsetDateTime.parse(request.getRenewalDate()));
        }

        entity.setUpdatedAt(OffsetDateTime.now());
        userPlanRepo.save(entity);
        log.info("Admin override applied to user {}: plan={}, status={}, postsLimit={}, accountsLimit={}",
                targetUserId, entity.getPlanType(), entity.getStatus(),
                entity.getCustomPostsLimit(), entity.getCustomAccountsLimit());
        return toResponse(entity);
    }

    // ─── Workspace-scoped helpers ────────────────────────────────────────────────

    /**
     * Returns the plan for the given workspace.
     * The plan is owned by workspace.ownerUserId; all workspace members share these limits.
     */
    @Transactional
    public UserPlanResponse getWorkspacePlan(String workspaceId) {
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        UserPlanEntity entity = getOrCreate(workspace.getOwnerUserId());
        return toResponse(entity);
    }

    /**
     * Changes the plan for the given workspace.
     * Only the workspace OWNER may call this (enforced via @RequiresRole in controller).
     */
    @Transactional
    public UserPlanResponse changeWorkspacePlan(String workspaceId, ChangePlanRequest request) {
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        return changePlan(workspace.getOwnerUserId(), request);
    }

    /**
     * Admin override keyed by workspaceId (instead of userId).
     */
    @Transactional
    public UserPlanResponse adminOverrideByWorkspace(String workspaceId, AdminPlanOverrideRequest request) {
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        return adminOverride(workspace.getOwnerUserId(), request);
    }

    private WorkspaceEntity requireWorkspace(String workspaceId) {
        return workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found: " + workspaceId, HttpStatus.NOT_FOUND));
    }

    // ─── Internal helpers ────────────────────────────────────────────────────────

    public UserPlanEntity getOrCreate(String userId) {
        return userPlanRepo.findByUserId(userId).orElseGet(() -> createTrial(userId));
    }

    private UserPlanEntity createTrial(String userId) {
        PlanConfigEntity trialConfig = planConfigRepo.findById(PlanType.TRIAL)
                .orElseThrow(() -> new SocialRavenException("TRIAL plan config missing", HttpStatus.INTERNAL_SERVER_ERROR));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime trialEnd = now.plusDays(trialConfig.getTrialDays() != null ? trialConfig.getTrialDays() : 14);

        UserPlanEntity entity = new UserPlanEntity();
        entity.setUserId(userId);
        entity.setPlanType(PlanType.TRIAL);
        entity.setStatus(PlanStatus.TRIALING);
        entity.setRenewalDate(trialEnd);
        entity.setTrialEndsAt(trialEnd);
        entity.setCancelAtPeriodEnd(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        userPlanRepo.save(entity);
        log.info("Created TRIAL plan for new user {}", userId);
        return entity;
    }

    public UserPlanResponse toResponse(UserPlanEntity entity) {
        PlanConfigEntity config = planConfigRepo.findById(entity.getPlanType())
                .orElseThrow(() -> new SocialRavenException("Plan config not found: " + entity.getPlanType(), HttpStatus.INTERNAL_SERVER_ERROR));

        // Effective limits: custom override takes precedence over plan default
        Integer effectivePostsLimit    = entity.getCustomPostsLimit()    != null ? entity.getCustomPostsLimit()    : config.getPostsPerMonth();
        Integer effectiveAccountsLimit = entity.getCustomAccountsLimit() != null ? entity.getCustomAccountsLimit() : config.getAccountsLimit();

        UserPlanResponse resp = new UserPlanResponse();
        resp.setCurrentPlan(entity.getPlanType().name());
        resp.setStatus(entity.getStatus().name());
        resp.setRenewalDate(entity.getRenewalDate().toString());
        resp.setStartDate(entity.getCreatedAt().toString());
        resp.setTrialEndsAt(entity.getTrialEndsAt() != null ? entity.getTrialEndsAt().toString() : null);
        resp.setCancelAtPeriodEnd(entity.isCancelAtPeriodEnd());
        resp.setStripeSubscriptionId(entity.getStripeSubscriptionId());
        resp.setPostsLimit(effectivePostsLimit);
        resp.setAccountsLimit(effectiveAccountsLimit);
        return resp;
    }
}
