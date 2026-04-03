package com.tonyghouse.socialraven.service.plan;

import com.tonyghouse.socialraven.constant.PlanStatus;
import com.tonyghouse.socialraven.constant.PlanType;
import com.tonyghouse.socialraven.constant.UserType;
import com.tonyghouse.socialraven.dto.plan.AdminPlanOverrideRequest;
import com.tonyghouse.socialraven.dto.plan.ChangePlanRequest;
import com.tonyghouse.socialraven.dto.plan.UserPlanResponse;
import com.tonyghouse.socialraven.entity.CompanyEntity;
import com.tonyghouse.socialraven.entity.PlanConfigEntity;
import com.tonyghouse.socialraven.entity.UserPlanEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.CompanyRepo;
import com.tonyghouse.socialraven.repo.PlanConfigRepo;
import com.tonyghouse.socialraven.repo.UserPlanRepo;
import com.tonyghouse.socialraven.repo.UserProfileRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.company.CompanyAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Autowired
    private UserProfileRepo userProfileRepo;

    @Autowired
    private CompanyRepo companyRepo;

    @Autowired
    private CompanyAccessService companyAccessService;

    @Transactional
    public UserPlanResponse getUserPlan(String userId) {
        String companyId = resolvePrimaryCompanyId(userId);
        return toResponse(getOrCreateForCompany(companyId));
    }

    @Transactional
    public UserPlanResponse changePlan(String userId, ChangePlanRequest request) {
        String companyId = resolvePrimaryCompanyId(userId);
        return changeCompanyPlan(companyId, request);
    }

    @Transactional
    public UserPlanResponse adminOverride(String targetUserId, AdminPlanOverrideRequest request) {
        String companyId = resolvePrimaryCompanyId(targetUserId);
        return adminOverrideByCompanyId(companyId, request);
    }

    @Transactional
    public UserPlanResponse getWorkspacePlan(String workspaceId) {
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        return toResponse(getOrCreateForCompany(workspace.getCompanyId()));
    }

    @Transactional
    public UserPlanResponse changeWorkspacePlan(String workspaceId, ChangePlanRequest request) {
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        return changeCompanyPlan(workspace.getCompanyId(), request);
    }

    @Transactional
    public UserPlanResponse adminOverrideByWorkspace(String workspaceId, AdminPlanOverrideRequest request) {
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        return adminOverrideByCompanyId(workspace.getCompanyId(), request);
    }

    public UserPlanEntity getOrCreateForCompany(String companyId) {
        return userPlanRepo.findByCompanyId(companyId).orElseGet(() -> {
            try {
                return createTrial(companyId);
            } catch (DataIntegrityViolationException e) {
                return userPlanRepo.findByCompanyId(companyId)
                        .orElseThrow(() -> new SocialRavenException("Plan unavailable for company: " + companyId, HttpStatus.INTERNAL_SERVER_ERROR));
            }
        });
    }

    private UserPlanResponse changeCompanyPlan(String companyId, ChangePlanRequest request) {
        PlanType newPlanType = request.getPlanType();
        if (newPlanType == null) {
            throw new SocialRavenException("planType is required", HttpStatus.BAD_REQUEST);
        }

        planConfigRepo.findById(newPlanType)
                .orElseThrow(() -> new SocialRavenException("Unknown plan: " + newPlanType, HttpStatus.BAD_REQUEST));

        UserPlanEntity entity = getOrCreateForCompany(companyId);
        if (entity.getPlanType() == newPlanType) {
            return toResponse(entity);
        }

        entity.setPlanType(newPlanType);
        if (newPlanType == PlanType.INFLUENCER_TRIAL || newPlanType == PlanType.AGENCY_TRIAL) {
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
        entity.setCustomPostsLimit(null);
        entity.setCustomAccountsLimit(null);

        userPlanRepo.save(entity);
        log.info("Company {} changed plan to {}", companyId, newPlanType);
        return toResponse(entity);
    }

    private UserPlanResponse adminOverrideByCompanyId(String companyId, AdminPlanOverrideRequest request) {
        UserPlanEntity entity = getOrCreateForCompany(companyId);

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
        log.info("Admin override applied to company {}: plan={}, status={}, postsLimit={}, accountsLimit={}",
                companyId, entity.getPlanType(), entity.getStatus(),
                entity.getCustomPostsLimit(), entity.getCustomAccountsLimit());
        return toResponse(entity);
    }

    private WorkspaceEntity requireWorkspace(String workspaceId) {
        return workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found: " + workspaceId, HttpStatus.NOT_FOUND));
    }

    private String resolvePrimaryCompanyId(String userId) {
        return companyAccessService.findPrimaryCompanyId(userId)
                .orElseThrow(() -> new SocialRavenException("No company found for user " + userId, HttpStatus.BAD_REQUEST));
    }

    private UserPlanEntity createTrial(String companyId) {
        CompanyEntity company = companyRepo.findById(companyId)
                .orElseThrow(() -> new SocialRavenException("Company not found: " + companyId, HttpStatus.BAD_REQUEST));

        PlanType trialPlanType = userProfileRepo.findById(company.getOwnerUserId())
                .map(profile -> profile.getUserType() == UserType.AGENCY ? PlanType.AGENCY_TRIAL : PlanType.INFLUENCER_TRIAL)
                .orElse(PlanType.INFLUENCER_TRIAL);

        PlanConfigEntity trialConfig = planConfigRepo.findById(trialPlanType)
                .orElseThrow(() -> new SocialRavenException(trialPlanType + " plan config missing", HttpStatus.INTERNAL_SERVER_ERROR));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime trialEnd = now.plusDays(trialConfig.getTrialDays() != null ? trialConfig.getTrialDays() : 14);

        UserPlanEntity entity = new UserPlanEntity();
        entity.setCompanyId(companyId);
        entity.setPlanType(trialPlanType);
        entity.setStatus(PlanStatus.TRIALING);
        entity.setRenewalDate(trialEnd);
        entity.setTrialEndsAt(trialEnd);
        entity.setCancelAtPeriodEnd(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        userPlanRepo.save(entity);
        log.info("Created {} plan for company {}", trialPlanType, companyId);
        return entity;
    }

    public UserPlanResponse toResponse(UserPlanEntity entity) {
        PlanConfigEntity config = planConfigRepo.findById(entity.getPlanType())
                .orElseThrow(() -> new SocialRavenException("Plan config not found: " + entity.getPlanType(), HttpStatus.INTERNAL_SERVER_ERROR));

        Integer effectivePostsLimit = entity.getCustomPostsLimit() != null ? entity.getCustomPostsLimit() : config.getPostsPerMonth();
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
