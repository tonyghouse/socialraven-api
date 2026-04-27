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
        return toResponse(getOrCreateForCompany(companyId), resolveSingleActiveWorkspace(companyId));
    }

    @Transactional
    public UserPlanResponse changePlan(String userId, ChangePlanRequest request) {
        String companyId = resolvePrimaryCompanyId(userId);
        return changeCompanyPlan(companyId, request, resolveSingleActiveWorkspace(companyId));
    }

    @Transactional
    public UserPlanResponse adminOverride(String targetUserId, AdminPlanOverrideRequest request) {
        String companyId = resolvePrimaryCompanyId(targetUserId);
        return adminOverrideByCompanyId(companyId, request, resolveSingleActiveWorkspace(companyId));
    }

    @Transactional
    public UserPlanResponse getWorkspacePlan(String workspaceId) {
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        return toResponse(getOrCreateForCompany(workspace.getCompanyId()), workspace);
    }

    @Transactional
    public UserPlanResponse changeWorkspacePlan(String workspaceId, ChangePlanRequest request) {
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        return changeCompanyPlan(workspace.getCompanyId(), request, workspace);
    }

    @Transactional
    public UserPlanResponse adminOverrideByWorkspace(String workspaceId, AdminPlanOverrideRequest request) {
        WorkspaceEntity workspace = requireWorkspace(workspaceId);
        return adminOverrideByWorkspace(workspace, request);
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

    private UserPlanResponse changeCompanyPlan(String companyId, ChangePlanRequest request, WorkspaceEntity responseWorkspace) {
        PlanType newPlanType = request.getPlanType();
        if (newPlanType == null) {
            throw new SocialRavenException("planType is required", HttpStatus.BAD_REQUEST);
        }

        ensurePlanExists(newPlanType);

        UserPlanEntity entity = getOrCreateForCompany(companyId);
        if (entity.getPlanType() == newPlanType) {
            return toResponse(entity, responseWorkspace);
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
        entity.setCustomXPostsLimit(null);

        userPlanRepo.save(entity);
        log.info("Company {} changed plan to {}", companyId, newPlanType);
        return toResponse(entity, responseWorkspace);
    }

    private UserPlanResponse adminOverrideByCompanyId(
            String companyId,
            AdminPlanOverrideRequest request,
            WorkspaceEntity responseWorkspace
    ) {
        UserPlanEntity entity = getOrCreateForCompany(companyId);

        if (request.getPlanType() != null) {
            ensurePlanExists(request.getPlanType());
            entity.setPlanType(request.getPlanType());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (request.getCustomPostsLimit() != null) {
            entity.setCustomPostsLimit(normalizeOverride(request.getCustomPostsLimit()));
        }
        if (request.getCustomAccountsLimit() != null) {
            entity.setCustomAccountsLimit(normalizeOverride(request.getCustomAccountsLimit()));
        }
        if (request.getCustomXPostsLimit() != null) {
            entity.setCustomXPostsLimit(normalizeOverride(request.getCustomXPostsLimit()));
        }
        if (request.getRenewalDate() != null) {
            entity.setRenewalDate(OffsetDateTime.parse(request.getRenewalDate()));
        }

        entity.setUpdatedAt(OffsetDateTime.now());
        userPlanRepo.save(entity);
        log.info("Admin override applied to company {}: plan={}, status={}, postsLimit={}, accountsLimit={}, xPostsLimit={}",
                companyId, entity.getPlanType(), entity.getStatus(),
                entity.getCustomPostsLimit(), entity.getCustomAccountsLimit(), entity.getCustomXPostsLimit());
        return toResponse(entity, responseWorkspace);
    }

    private UserPlanResponse adminOverrideByWorkspace(WorkspaceEntity workspace, AdminPlanOverrideRequest request) {
        String companyId = workspace.getCompanyId();
        UserPlanEntity entity = getOrCreateForCompany(companyId);
        boolean companyPlanChanged = false;

        if (request.getPlanType() != null) {
            ensurePlanExists(request.getPlanType());
            entity.setPlanType(request.getPlanType());
            companyPlanChanged = true;
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
            companyPlanChanged = true;
        }
        if (request.getCustomPostsLimit() != null) {
            entity.setCustomPostsLimit(normalizeOverride(request.getCustomPostsLimit()));
            companyPlanChanged = true;
        }
        if (request.getCustomAccountsLimit() != null) {
            entity.setCustomAccountsLimit(normalizeOverride(request.getCustomAccountsLimit()));
            companyPlanChanged = true;
        }
        if (request.getRenewalDate() != null) {
            entity.setRenewalDate(OffsetDateTime.parse(request.getRenewalDate()));
            companyPlanChanged = true;
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (companyPlanChanged) {
            entity.setUpdatedAt(now);
            userPlanRepo.save(entity);
        }

        if (request.getCustomXPostsLimit() != null) {
            workspace.setCustomXPostsLimit(normalizeOverride(request.getCustomXPostsLimit()));
            workspace.setUpdatedAt(now);
            workspaceRepo.save(workspace);
        }

        log.info("Admin workspace override applied: workspaceId={}, companyId={}, plan={}, status={}, postsLimit={}, accountsLimit={}, workspaceXPostsLimit={}",
                workspace.getId(), companyId, entity.getPlanType(), entity.getStatus(),
                entity.getCustomPostsLimit(), entity.getCustomAccountsLimit(), workspace.getCustomXPostsLimit());
        return toResponse(entity, workspace);
    }

    private WorkspaceEntity requireWorkspace(String workspaceId) {
        return workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found: " + workspaceId, HttpStatus.NOT_FOUND));
    }

    private String resolvePrimaryCompanyId(String userId) {
        return companyAccessService.findPrimaryCompanyId(userId)
                .orElseThrow(() -> new SocialRavenException("No company found for user " + userId, HttpStatus.BAD_REQUEST));
    }

    private WorkspaceEntity resolveSingleActiveWorkspace(String companyId) {
        var workspaces = workspaceRepo.findAllByCompanyIdAndDeletedAtIsNull(companyId);
        return workspaces.size() == 1 ? workspaces.get(0) : null;
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

    private Integer normalizeOverride(Integer value) {
        return value != null && value < 0 ? null : value;
    }

    private void ensurePlanExists(PlanType planType) {
        planConfigRepo.findById(planType)
                .orElseThrow(() -> new SocialRavenException("Unknown plan: " + planType, HttpStatus.BAD_REQUEST));
    }

    private PlanConfigEntity requirePlanConfig(PlanType planType) {
        return planConfigRepo.findById(planType)
                .orElseThrow(() -> new SocialRavenException("Plan config not found: " + planType, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    public Integer resolveEffectivePostsLimit(UserPlanEntity entity, PlanConfigEntity config) {
        return entity.getCustomPostsLimit() != null ? entity.getCustomPostsLimit() : config.getPostsPerMonth();
    }

    public Integer resolveEffectiveAccountsLimit(UserPlanEntity entity, PlanConfigEntity config) {
        return entity.getCustomAccountsLimit() != null ? entity.getCustomAccountsLimit() : config.getAccountsLimit();
    }

    public Integer resolveEffectiveXPostsLimit(UserPlanEntity entity, PlanConfigEntity config, WorkspaceEntity workspace) {
        if (workspace != null && workspace.getCustomXPostsLimit() != null) {
            return workspace.getCustomXPostsLimit();
        }
        if (entity.getCustomXPostsLimit() != null) {
            return entity.getCustomXPostsLimit();
        }
        return config.getXPostsPerMonth();
    }

    public UserPlanResponse toResponse(UserPlanEntity entity) {
        return toResponse(entity, null);
    }

    public UserPlanResponse toResponse(UserPlanEntity entity, WorkspaceEntity workspace) {
        PlanConfigEntity config = requirePlanConfig(entity.getPlanType());

        UserPlanResponse resp = new UserPlanResponse();
        resp.setCurrentPlan(entity.getPlanType().name());
        resp.setStatus(entity.getStatus().name());
        resp.setRenewalDate(entity.getRenewalDate().toString());
        resp.setStartDate(entity.getCreatedAt().toString());
        resp.setTrialEndsAt(entity.getTrialEndsAt() != null ? entity.getTrialEndsAt().toString() : null);
        resp.setCancelAtPeriodEnd(entity.isCancelAtPeriodEnd());
        resp.setPaddleSubscriptionId(entity.getPaddleSubscriptionId());
        resp.setPostsLimit(resolveEffectivePostsLimit(entity, config));
        resp.setAccountsLimit(resolveEffectiveAccountsLimit(entity, config));
        resp.setXPostsLimit(resolveEffectiveXPostsLimit(entity, config, workspace));
        return resp;
    }
}
