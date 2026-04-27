package com.tonyghouse.socialraven.service.plan;

import com.tonyghouse.socialraven.dto.plan.UsageStatsResponse;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.entity.PlanConfigEntity;
import com.tonyghouse.socialraven.entity.UserPlanEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.repo.PlanConfigRepo;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.company.CompanyAccessService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class UsageService {

    @Autowired
    private PostCollectionRepo postCollectionRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private OAuthInfoRepo oAuthInfoRepo;

    @Autowired
    private UserPlanService userPlanService;

    @Autowired
    private PlanConfigRepo planConfigRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private CompanyAccessService companyAccessService;

    public UsageStatsResponse getUsageStats(String userId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceEntity workspace = workspaceId != null
                ? workspaceRepo.findById(workspaceId).orElse(null)
                : null;
        String companyId = workspace != null
                ? workspace.getCompanyId()
                : companyAccessService.findPrimaryCompanyId(userId)
                        .orElseThrow(() -> new SocialRavenException("No company context available", HttpStatus.BAD_REQUEST));

        if (workspace == null) {
            workspace = workspaceRepo.findAllByCompanyIdAndDeletedAtIsNull(companyId).stream()
                    .findFirst()
                    .orElse(null);
            workspaceId = workspace != null ? workspace.getId() : null;
        }

        UserPlanEntity planEntity = userPlanService.getOrCreateForCompany(companyId);

        PlanConfigEntity config = planConfigRepo.findById(planEntity.getPlanType())
                .orElseThrow(() -> new SocialRavenException("Plan config not found", HttpStatus.INTERNAL_SERVER_ERROR));

        OffsetDateTime startOfMonth = OffsetDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        long postsUsed = workspaceId != null
                ? postCollectionRepo.countNonDraftPostsFromMonth(workspaceId, startOfMonth)
                : 0;

        long accountsCount = workspaceId != null ? oAuthInfoRepo.findAllByWorkspaceId(workspaceId).size() : 0;

        long xPostsUsed = workspaceId != null
                ? postRepo.countWorkspacePostsByProviderFromMonth(workspaceId, Provider.X, startOfMonth)
                : 0;

        Integer postsLimit = userPlanService.resolveEffectivePostsLimit(planEntity, config);
        Integer accountsLimit = userPlanService.resolveEffectiveAccountsLimit(planEntity, config);
        Integer xPostsLimit = userPlanService.resolveEffectiveXPostsLimit(planEntity, config, workspace);

        int workspacesOwned = workspaceRepo.findAllByCompanyIdAndDeletedAtIsNull(companyId).size();

        UsageStatsResponse resp = new UsageStatsResponse();
        resp.setPostsUsedThisMonth(postsUsed);
        resp.setPostsLimit(postsLimit);
        resp.setConnectedAccountsCount(accountsCount);
        resp.setAccountsLimit(accountsLimit);
        resp.setXPostsUsedThisMonth(xPostsUsed);
        resp.setXPostsLimit(xPostsLimit);
        resp.setWorkspacesOwned(workspacesOwned);
        resp.setMaxWorkspaces(config.getMaxWorkspaces());
        return resp;
    }
}
