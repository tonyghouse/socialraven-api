package com.tonyghouse.socialraven.service.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.PlanStatus;
import com.tonyghouse.socialraven.constant.PlanType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.plan.UsageStatsResponse;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.entity.PlanConfigEntity;
import com.tonyghouse.socialraven.entity.UserPlanEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.repo.PlanConfigRepo;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UsageServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void getUsageStatsReturnsWorkspaceScopedXUsageAndLimit() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostRepo postRepo = mock(PostRepo.class);
        OAuthInfoRepo oAuthInfoRepo = mock(OAuthInfoRepo.class);
        UserPlanService userPlanService = mock(UserPlanService.class);
        PlanConfigRepo planConfigRepo = mock(PlanConfigRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);

        UsageService service = new UsageService();
        ReflectionTestUtils.setField(service, "postCollectionRepo", postCollectionRepo);
        ReflectionTestUtils.setField(service, "postRepo", postRepo);
        ReflectionTestUtils.setField(service, "oAuthInfoRepo", oAuthInfoRepo);
        ReflectionTestUtils.setField(service, "userPlanService", userPlanService);
        ReflectionTestUtils.setField(service, "planConfigRepo", planConfigRepo);
        ReflectionTestUtils.setField(service, "workspaceRepo", workspaceRepo);

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId("workspace_1");
        workspace.setCompanyId("company_1");
        workspace.setCustomXPostsLimit(400);

        UserPlanEntity plan = new UserPlanEntity();
        plan.setCompanyId("company_1");
        plan.setPlanType(PlanType.AGENCY_BASE);
        plan.setStatus(PlanStatus.ACTIVE);

        PlanConfigEntity config = new PlanConfigEntity();
        config.setPlanType(PlanType.AGENCY_BASE);
        config.setPostsPerMonth(300);
        config.setAccountsLimit(10);
        config.setXPostsPerMonth(300);
        config.setMaxWorkspaces(3);
        config.setPriceUsd(BigDecimal.valueOf(79));

        OAuthInfoEntity xAccount = new OAuthInfoEntity();
        xAccount.setProvider(Provider.X);
        OAuthInfoEntity linkedInAccount = new OAuthInfoEntity();
        linkedInAccount.setProvider(Provider.LINKEDIN);

        WorkspaceContext.set("workspace_1", WorkspaceRole.OWNER);

        when(workspaceRepo.findById("workspace_1")).thenReturn(Optional.of(workspace));
        when(userPlanService.getOrCreateForCompany("company_1")).thenReturn(plan);
        when(planConfigRepo.findById(PlanType.AGENCY_BASE)).thenReturn(Optional.of(config));
        when(postCollectionRepo.countNonDraftPostsFromMonth(eq("workspace_1"), any())).thenReturn(12L);
        when(postRepo.countWorkspacePostsByProviderFromMonth(eq("workspace_1"), eq(Provider.X), any())).thenReturn(42L);
        when(oAuthInfoRepo.findAllByWorkspaceId("workspace_1")).thenReturn(List.of(xAccount, linkedInAccount));
        when(userPlanService.resolveEffectivePostsLimit(plan, config)).thenReturn(300);
        when(userPlanService.resolveEffectiveAccountsLimit(plan, config)).thenReturn(10);
        when(userPlanService.resolveEffectiveXPostsLimit(plan, config, workspace)).thenReturn(400);
        when(workspaceRepo.findAllByCompanyIdAndDeletedAtIsNull("company_1")).thenReturn(List.of(workspace));

        UsageStatsResponse response = service.getUsageStats("user_1");

        assertThat(response.getPostsUsedThisMonth()).isEqualTo(12);
        assertThat(response.getPostsLimit()).isEqualTo(300);
        assertThat(response.getConnectedAccountsCount()).isEqualTo(2);
        assertThat(response.getAccountsLimit()).isEqualTo(10);
        assertThat(response.getXPostsUsedThisMonth()).isEqualTo(42);
        assertThat(response.getXPostsLimit()).isEqualTo(400);
        assertThat(response.getWorkspacesOwned()).isEqualTo(1);
        assertThat(response.getMaxWorkspaces()).isEqualTo(3);
    }
}
