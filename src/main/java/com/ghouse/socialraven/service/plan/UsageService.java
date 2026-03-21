package com.ghouse.socialraven.service.plan;

import com.ghouse.socialraven.constant.PostCollectionStatus;
import com.ghouse.socialraven.dto.plan.UsageStatsResponse;
import com.ghouse.socialraven.entity.PlanConfigEntity;
import com.ghouse.socialraven.entity.UserPlanEntity;
import com.ghouse.socialraven.exception.SocialRavenException;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.repo.PlanConfigRepo;
import com.ghouse.socialraven.repo.PostCollectionRepo;
import com.ghouse.socialraven.util.WorkspaceContext;
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

    public UsageStatsResponse getUsageStats(String userId) {
        UserPlanEntity planEntity = userPlanService.getOrCreate(userId);

        PlanConfigEntity config = planConfigRepo.findById(planEntity.getPlanType())
                .orElseThrow(() -> new SocialRavenException("Plan config not found", HttpStatus.INTERNAL_SERVER_ERROR));

        String workspaceId = WorkspaceContext.getWorkspaceId();

        OffsetDateTime startOfMonth = OffsetDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        long postsUsed = postCollectionRepo.countNonDraftPostsFromMonth(
                workspaceId, PostCollectionStatus.DRAFT, startOfMonth);

        long accountsCount = oAuthInfoRepo.findAllByWorkspaceId(workspaceId).size();

        Integer postsLimit    = planEntity.getCustomPostsLimit()    != null ? planEntity.getCustomPostsLimit()    : config.getPostsPerMonth();
        Integer accountsLimit = planEntity.getCustomAccountsLimit() != null ? planEntity.getCustomAccountsLimit() : config.getAccountsLimit();

        UsageStatsResponse resp = new UsageStatsResponse();
        resp.setPostsUsedThisMonth(postsUsed);
        resp.setPostsLimit(postsLimit);
        resp.setConnectedAccountsCount(accountsCount);
        resp.setAccountsLimit(accountsLimit);
        return resp;
    }
}
