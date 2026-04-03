package com.tonyghouse.socialraven.service.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.PostApprovalStage;
import com.tonyghouse.socialraven.constant.PostCollectionType;
import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.constant.PostStatus;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.RecoveryState;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.workspace.AgencyOpsResponse;
import com.tonyghouse.socialraven.entity.CompanyEntity;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.entity.WorkspaceUserCapabilityEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.CompanyRepo;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.repo.WorkspaceUserCapabilityRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AgencyOpsServiceTest {

    @Test
    void getAgencyOpsAggregatesCrossWorkspaceQueueRiskAndWorkload() {
        WorkspaceMemberRepo workspaceMemberRepo = mock(WorkspaceMemberRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        WorkspaceUserCapabilityRepo workspaceUserCapabilityRepo = mock(WorkspaceUserCapabilityRepo.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);

        AgencyOpsService service = createService(
                workspaceMemberRepo,
                workspaceRepo,
                companyRepo,
                postCollectionRepo,
                workspaceCapabilityService,
                workspaceUserCapabilityRepo,
                clerkUserService
        );

        OffsetDateTime now = OffsetDateTime.now();

        when(workspaceMemberRepo.findAllByUserId("agent_1")).thenReturn(List.of(
                membership("workspace_1", "agent_1", WorkspaceRole.ADMIN),
                membership("workspace_2", "agent_1", WorkspaceRole.OWNER)
        ));
        when(workspaceRepo.findAllById(any())).thenReturn(List.of(
                workspace("workspace_1", "Client Alpha", "company_1"),
                workspace("workspace_2", "Client Beta", "company_2")
        ));
        when(companyRepo.findAllById(any())).thenReturn(List.of(
                company("company_1", "Alpha Group"),
                company("company_2", "Beta Holdings")
        ));
        when(workspaceCapabilityService.getEffectiveCapabilities("workspace_1", "agent_1", WorkspaceRole.ADMIN))
                .thenReturn(EnumSet.of(WorkspaceCapability.APPROVE_POSTS, WorkspaceCapability.REQUEST_CHANGES));
        when(workspaceCapabilityService.getEffectiveCapabilities("workspace_2", "agent_1", WorkspaceRole.OWNER))
                .thenReturn(EnumSet.of(WorkspaceCapability.APPROVE_POSTS, WorkspaceCapability.REQUEST_CHANGES));

        when(workspaceMemberRepo.findAllByWorkspaceIdIn(List.of("workspace_1", "workspace_2"))).thenReturn(List.of(
                membership("workspace_1", "owner_alpha", WorkspaceRole.OWNER),
                membership("workspace_1", "admin_alpha", WorkspaceRole.ADMIN),
                membership("workspace_1", "editor_alpha", WorkspaceRole.EDITOR),
                membership("workspace_2", "owner_beta", WorkspaceRole.OWNER),
                membership("workspace_2", "admin_beta", WorkspaceRole.ADMIN)
        ));
        when(workspaceUserCapabilityRepo.findAllByWorkspaceIdInAndCapability(
                List.of("workspace_1", "workspace_2"),
                WorkspaceCapability.APPROVE_POSTS
        )).thenReturn(List.of(explicitApprover("workspace_1", "editor_alpha")));

        PostCollectionEntity overdueReview = reviewCollection(
                101L,
                "workspace_1",
                PostReviewStatus.IN_REVIEW,
                now.plusHours(12),
                now.minusHours(1),
                null
        );
        PostCollectionEntity escalatedReview = reviewCollection(
                202L,
                "workspace_2",
                PostReviewStatus.IN_REVIEW,
                now.plusHours(6),
                now.minusHours(2),
                now.minusHours(1)
        );
        PostCollectionEntity changesRequested = reviewCollection(
                303L,
                "workspace_1",
                PostReviewStatus.CHANGES_REQUESTED,
                now.plusHours(18),
                null,
                null
        );
        PostCollectionEntity recoveryRequired = scheduledRiskCollection(
                404L,
                "workspace_2",
                now.plusDays(2),
                RecoveryState.RECOVERY_REQUIRED
        );

        when(postCollectionRepo.findAgencyOpsReviewCollections(
                List.of("workspace_1", "workspace_2"),
                List.of(PostReviewStatus.IN_REVIEW, PostReviewStatus.CHANGES_REQUESTED)
        )).thenReturn(List.of(overdueReview, escalatedReview, changesRequested));
        when(postCollectionRepo.findAgencyOpsScheduledCollections(
                eq(List.of("workspace_1", "workspace_2")),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(recoveryRequired));

        when(clerkUserService.getUserProfile("owner_alpha"))
                .thenReturn(new ClerkUserService.UserProfile("Owner", "Alpha", "owner.alpha@example.com"));
        when(clerkUserService.getUserProfile("admin_alpha"))
                .thenReturn(new ClerkUserService.UserProfile("Admin", "Alpha", "admin.alpha@example.com"));
        when(clerkUserService.getUserProfile("editor_alpha"))
                .thenReturn(new ClerkUserService.UserProfile("Editor", "Alpha", "editor.alpha@example.com"));
        when(clerkUserService.getUserProfile("owner_beta"))
                .thenReturn(new ClerkUserService.UserProfile("Owner", "Beta", "owner.beta@example.com"));
        when(clerkUserService.getUserProfile("admin_beta"))
                .thenReturn(new ClerkUserService.UserProfile("Admin", "Beta", "admin.beta@example.com"));

        AgencyOpsResponse response = service.getAgencyOps("agent_1");

        assertThat(response.getSummary().getWorkspaceCount()).isEqualTo(2);
        assertThat(response.getSummary().getPendingApprovalCount()).isEqualTo(2);
        assertThat(response.getSummary().getOverdueApprovalCount()).isEqualTo(1);
        assertThat(response.getSummary().getEscalatedApprovalCount()).isEqualTo(1);
        assertThat(response.getSummary().getAtRiskPublishCount()).isEqualTo(4);
        assertThat(response.getSummary().getApproverCount()).isEqualTo(5);

        assertThat(response.getQueue()).hasSize(2);
        assertThat(response.getQueue().get(0).getCollectionId()).isEqualTo(202L);
        assertThat(response.getQueue().get(0).getAttentionStatus()).isEqualTo("ESCALATED");
        assertThat(response.getQueue().get(1).getCollectionId()).isEqualTo(101L);
        assertThat(response.getQueue().get(1).getEligibleApproverUserIds())
                .containsExactly("admin_alpha", "editor_alpha", "owner_alpha");

        assertThat(response.getOverdueQueue()).hasSize(2);

        assertThat(response.getPublishRisk())
                .extracting(AgencyOpsResponse.PublishRiskItem::getRiskType)
                .containsExactly("APPROVAL_PENDING", "APPROVAL_PENDING", "CHANGES_REQUESTED", "RECOVERY_REQUIRED");
        assertThat(response.getPublishRisk().get(2).getEligibleApproverUserIds())
                .containsExactly("admin_alpha", "editor_alpha", "owner_alpha");
        assertThat(response.getPublishRisk().get(3).getEligibleApproverUserIds()).isEmpty();

        assertThat(response.getWorkload()).hasSize(5);
        assertThat(response.getWorkload())
                .filteredOn(item -> item.getUserId().equals("editor_alpha"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getPendingApprovalCount()).isEqualTo(1);
                    assertThat(item.getOverdueApprovalCount()).isEqualTo(1);
                    assertThat(item.getEscalatedApprovalCount()).isZero();
                });

        assertThat(response.getWorkspaceHealth())
                .filteredOn(item -> item.getWorkspaceId().equals("workspace_1"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getPendingApprovalCount()).isEqualTo(1);
                    assertThat(item.getOverdueApprovalCount()).isEqualTo(1);
                    assertThat(item.getChangesRequestedCount()).isEqualTo(1);
                    assertThat(item.getAtRiskPublishCount()).isEqualTo(2);
                    assertThat(item.getHealthStatus()).isEqualTo("CRITICAL");
                });
    }

    @Test
    void getAgencyOpsRejectsUsersWithoutApprovalWorkflowAccess() {
        WorkspaceMemberRepo workspaceMemberRepo = mock(WorkspaceMemberRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        WorkspaceUserCapabilityRepo workspaceUserCapabilityRepo = mock(WorkspaceUserCapabilityRepo.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);

        AgencyOpsService service = createService(
                workspaceMemberRepo,
                workspaceRepo,
                companyRepo,
                postCollectionRepo,
                workspaceCapabilityService,
                workspaceUserCapabilityRepo,
                clerkUserService
        );

        when(workspaceMemberRepo.findAllByUserId("viewer_1"))
                .thenReturn(List.of(membership("workspace_1", "viewer_1", WorkspaceRole.READ_ONLY)));
        when(workspaceRepo.findAllById(any()))
                .thenReturn(List.of(workspace("workspace_1", "Client Alpha", "company_1")));
        when(companyRepo.findAllById(any()))
                .thenReturn(List.of(company("company_1", "Alpha Group")));
        when(workspaceCapabilityService.getEffectiveCapabilities("workspace_1", "viewer_1", WorkspaceRole.READ_ONLY))
                .thenReturn(EnumSet.noneOf(WorkspaceCapability.class));

        assertThatThrownBy(() -> service.getAgencyOps("viewer_1"))
                .isInstanceOf(SocialRavenException.class)
                .hasMessage("Approval workflow access is required");
    }

    private AgencyOpsService createService(WorkspaceMemberRepo workspaceMemberRepo,
                                           WorkspaceRepo workspaceRepo,
                                           CompanyRepo companyRepo,
                                           PostCollectionRepo postCollectionRepo,
                                           WorkspaceCapabilityService workspaceCapabilityService,
                                           WorkspaceUserCapabilityRepo workspaceUserCapabilityRepo,
                                           ClerkUserService clerkUserService) {
        AgencyOpsService service = new AgencyOpsService();
        ReflectionTestUtils.setField(service, "workspaceMemberRepo", workspaceMemberRepo);
        ReflectionTestUtils.setField(service, "workspaceRepo", workspaceRepo);
        ReflectionTestUtils.setField(service, "companyRepo", companyRepo);
        ReflectionTestUtils.setField(service, "postCollectionRepo", postCollectionRepo);
        ReflectionTestUtils.setField(service, "workspaceCapabilityService", workspaceCapabilityService);
        ReflectionTestUtils.setField(service, "workspaceUserCapabilityRepo", workspaceUserCapabilityRepo);
        ReflectionTestUtils.setField(service, "clerkUserService", clerkUserService);
        return service;
    }

    private WorkspaceMemberEntity membership(String workspaceId, String userId, WorkspaceRole role) {
        WorkspaceMemberEntity entity = new WorkspaceMemberEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setUserId(userId);
        entity.setRole(role);
        return entity;
    }

    private WorkspaceEntity workspace(String workspaceId, String name, String companyId) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setId(workspaceId);
        entity.setName(name);
        entity.setCompanyId(companyId);
        return entity;
    }

    private CompanyEntity company(String companyId, String name) {
        CompanyEntity entity = new CompanyEntity();
        entity.setId(companyId);
        entity.setName(name);
        return entity;
    }

    private WorkspaceUserCapabilityEntity explicitApprover(String workspaceId, String userId) {
        WorkspaceUserCapabilityEntity entity = new WorkspaceUserCapabilityEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setUserId(userId);
        entity.setCapability(WorkspaceCapability.APPROVE_POSTS);
        return entity;
    }

    private PostCollectionEntity reviewCollection(Long id,
                                                  String workspaceId,
                                                  PostReviewStatus reviewStatus,
                                                  OffsetDateTime scheduledTime,
                                                  OffsetDateTime nextApprovalReminderAt,
                                                  OffsetDateTime approvalEscalatedAt) {
        PostCollectionEntity entity = new PostCollectionEntity();
        entity.setId(id);
        entity.setWorkspaceId(workspaceId);
        entity.setDescription("Review item " + id);
        entity.setDraft(true);
        entity.setReviewStatus(reviewStatus);
        entity.setScheduledTime(scheduledTime);
        entity.setReviewSubmittedAt(scheduledTime.minusDays(1));
        entity.setNextApprovalReminderAt(nextApprovalReminderAt);
        entity.setApprovalEscalatedAt(approvalEscalatedAt);
        entity.setPostCollectionType(PostCollectionType.TEXT);
        entity.setRequiredApprovalSteps(2);
        entity.setCompletedApprovalSteps(reviewStatus == PostReviewStatus.CHANGES_REQUESTED ? 0 : 1);
        entity.setNextApprovalStage(PostApprovalStage.OWNER_FINAL);

        PostEntity post = new PostEntity();
        post.setPostCollection(entity);
        post.setProvider(Provider.LINKEDIN);
        post.setPostStatus(PostStatus.DRAFT);
        entity.setPosts(List.of(post));
        return entity;
    }

    private PostCollectionEntity scheduledRiskCollection(Long id,
                                                         String workspaceId,
                                                         OffsetDateTime scheduledTime,
                                                         RecoveryState recoveryState) {
        PostCollectionEntity entity = new PostCollectionEntity();
        entity.setId(id);
        entity.setWorkspaceId(workspaceId);
        entity.setDescription("Scheduled risk " + id);
        entity.setDraft(false);
        entity.setReviewStatus(PostReviewStatus.APPROVED);
        entity.setScheduledTime(scheduledTime);
        entity.setPostCollectionType(PostCollectionType.IMAGE);
        entity.setFailureState(recoveryState);

        PostEntity post = new PostEntity();
        post.setPostCollection(entity);
        post.setProvider(Provider.INSTAGRAM);
        post.setPostStatus(PostStatus.FAILED);
        entity.setPosts(List.of(post));
        return entity;
    }
}
