package com.tonyghouse.socialraven.service.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.PlanStatus;
import com.tonyghouse.socialraven.constant.PlanType;
import com.tonyghouse.socialraven.dto.plan.AdminPlanOverrideRequest;
import com.tonyghouse.socialraven.dto.plan.UserPlanResponse;
import com.tonyghouse.socialraven.entity.PlanConfigEntity;
import com.tonyghouse.socialraven.entity.UserPlanEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.repo.PlanConfigRepo;
import com.tonyghouse.socialraven.repo.UserPlanRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserPlanServiceTest {

    @Test
    void toResponsePrefersWorkspaceXOverrideOverCompanyOverrideAndPlanDefault() {
        PlanConfigRepo planConfigRepo = mock(PlanConfigRepo.class);

        UserPlanService service = new UserPlanService();
        ReflectionTestUtils.setField(service, "planConfigRepo", planConfigRepo);

        PlanConfigEntity config = planConfig(PlanType.INFLUENCER_BASE, 100, 5, 150);
        when(planConfigRepo.findById(PlanType.INFLUENCER_BASE)).thenReturn(Optional.of(config));

        UserPlanEntity plan = companyPlan("company_1", PlanType.INFLUENCER_BASE);
        plan.setCustomPostsLimit(220);
        plan.setCustomAccountsLimit(8);
        plan.setCustomXPostsLimit(250);

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId("workspace_1");
        workspace.setCompanyId("company_1");
        workspace.setCustomXPostsLimit(400);

        UserPlanResponse workspaceScoped = service.toResponse(plan, workspace);
        UserPlanResponse companyScoped = service.toResponse(plan);

        assertThat(workspaceScoped.getPostsLimit()).isEqualTo(220);
        assertThat(workspaceScoped.getAccountsLimit()).isEqualTo(8);
        assertThat(workspaceScoped.getXPostsLimit()).isEqualTo(400);

        assertThat(companyScoped.getXPostsLimit()).isEqualTo(250);
    }

    @Test
    void adminOverrideByWorkspaceStoresWorkspaceSpecificXLimitWithoutMutatingCompanyXOverride() {
        UserPlanRepo userPlanRepo = mock(UserPlanRepo.class);
        PlanConfigRepo planConfigRepo = mock(PlanConfigRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);

        UserPlanService service = new UserPlanService();
        ReflectionTestUtils.setField(service, "userPlanRepo", userPlanRepo);
        ReflectionTestUtils.setField(service, "planConfigRepo", planConfigRepo);
        ReflectionTestUtils.setField(service, "workspaceRepo", workspaceRepo);

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId("workspace_1");
        workspace.setCompanyId("company_1");
        workspace.setName("Main");
        workspace.setCreatedAt(OffsetDateTime.now().minusDays(5));
        workspace.setUpdatedAt(OffsetDateTime.now().minusDays(1));

        UserPlanEntity companyPlan = companyPlan("company_1", PlanType.INFLUENCER_BASE);
        companyPlan.setCustomXPostsLimit(250);

        PlanConfigEntity config = planConfig(PlanType.INFLUENCER_BASE, 100, 5, 150);

        when(workspaceRepo.findById("workspace_1")).thenReturn(Optional.of(workspace));
        when(userPlanRepo.findByCompanyId("company_1")).thenReturn(Optional.of(companyPlan));
        when(planConfigRepo.findById(PlanType.INFLUENCER_BASE)).thenReturn(Optional.of(config));
        when(workspaceRepo.save(any(WorkspaceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminPlanOverrideRequest request = new AdminPlanOverrideRequest();
        request.setCustomXPostsLimit(400);

        UserPlanResponse response = service.adminOverrideByWorkspace("workspace_1", request);

        verify(workspaceRepo).save(workspace);
        verify(userPlanRepo, never()).save(any(UserPlanEntity.class));
        assertThat(workspace.getCustomXPostsLimit()).isEqualTo(400);
        assertThat(companyPlan.getCustomXPostsLimit()).isEqualTo(250);
        assertThat(response.getXPostsLimit()).isEqualTo(400);
    }

    private static UserPlanEntity companyPlan(String companyId, PlanType planType) {
        UserPlanEntity entity = new UserPlanEntity();
        OffsetDateTime now = OffsetDateTime.now();
        entity.setCompanyId(companyId);
        entity.setPlanType(planType);
        entity.setStatus(PlanStatus.ACTIVE);
        entity.setRenewalDate(now.plusDays(30));
        entity.setCreatedAt(now.minusDays(15));
        entity.setUpdatedAt(now.minusDays(1));
        return entity;
    }

    private static PlanConfigEntity planConfig(PlanType planType, int postsLimit, int accountsLimit, int xPostsLimit) {
        PlanConfigEntity config = new PlanConfigEntity();
        config.setPlanType(planType);
        config.setPostsPerMonth(postsLimit);
        config.setAccountsLimit(accountsLimit);
        config.setXPostsPerMonth(xPostsLimit);
        config.setMaxWorkspaces(1);
        config.setPriceUsd(BigDecimal.valueOf(12));
        return config;
    }
}
