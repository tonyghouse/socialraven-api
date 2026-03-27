package com.tonyghouse.socialraven.service.plan;

import com.tonyghouse.socialraven.constant.PostCollectionStatus;
import com.tonyghouse.socialraven.dto.plan.UsageStatsResponse;
import com.tonyghouse.socialraven.entity.PlanConfigEntity;
import com.tonyghouse.socialraven.entity.UserPlanEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.repo.PlanConfigRepo;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
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
    private OAuthInfoRepo oAuthInfoRepo;

    @Autowired
    private UserPlanService userPlanService;

    @Autowired
    private PlanConfigRepo planConfigRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    public UsageStatsResponse getUsageStats(String userId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();

        // Plan belongs to workspace owner; all members share the same limits
        String planOwnerUserId = (workspaceId != null)
                ? workspaceRepo.findById(workspaceId)
                        .map(ws -> ws.getOwnerUserId())
                        .orElse(userId)
                : userId;

        UserPlanEntity planEntity = userPlanService.getOrCreate(planOwnerUserId, workspaceId);

        PlanConfigEntity config = planConfigRepo.findById(planEntity.getPlanType())
                .orElseThrow(() -> new SocialRavenException("Plan config not found", HttpStatus.INTERNAL_SERVER_ERROR));

        OffsetDateTime startOfMonth = OffsetDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        long postsUsed = postCollectionRepo.countNonDraftPostsFromMonth(
                workspaceId, PostCollectionStatus.DRAFT, startOfMonth);

        long accountsCount = oAuthInfoRepo.findAllByWorkspaceId(workspaceId).size();

        Integer postsLimit    = planEntity.getCustomPostsLimit()    != null ? planEntity.getCustomPostsLimit()    : config.getPostsPerMonth();
        Integer accountsLimit = planEntity.getCustomAccountsLimit() != null ? planEntity.getCustomAccountsLimit() : config.getAccountsLimit();

        int workspacesOwned = workspaceRepo.findAllByOwnerUserId(planOwnerUserId).size();

        UsageStatsResponse resp = new UsageStatsResponse();
        resp.setPostsUsedThisMonth(postsUsed);
        resp.setPostsLimit(postsLimit);
        resp.setConnectedAccountsCount(accountsCount);
        resp.setAccountsLimit(accountsLimit);
        resp.setWorkspacesOwned(workspacesOwned);
        resp.setMaxWorkspaces(config.getMaxWorkspaces());
        return resp;
    }
}
