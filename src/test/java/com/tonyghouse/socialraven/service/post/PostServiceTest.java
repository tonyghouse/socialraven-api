package com.tonyghouse.socialraven.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.constant.PostApprovalStage;
import com.tonyghouse.socialraven.constant.PostCollectionType;
import com.tonyghouse.socialraven.constant.PostCollectionVersionEvent;
import com.tonyghouse.socialraven.constant.PostReviewAction;
import com.tonyghouse.socialraven.constant.PostActorType;
import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.constant.PostStatus;
import com.tonyghouse.socialraven.dto.PostCollectionVersionResponse;
import com.tonyghouse.socialraven.constant.WorkspaceApprovalMode;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.UpdatePostCollectionRequest;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostCollectionReviewHistoryEntity;
import com.tonyghouse.socialraven.entity.PostCollectionVersionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.PostCollectionReviewHistoryRepo;
import com.tonyghouse.socialraven.repo.PostMediaRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import com.tonyghouse.socialraven.service.workspace.WorkspaceApprovalRuleService;
import com.tonyghouse.socialraven.service.workspace.WorkspaceCapabilityService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

class PostServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void updatePostCollectionRequiresExplicitConfirmationForApprovalLockedMaterialEdits() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewHistoryRepo reviewHistoryRepo = mock(PostCollectionReviewHistoryRepo.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        WorkspaceApprovalRuleService workspaceApprovalRuleService = mock(WorkspaceApprovalRuleService.class);
        WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot =
                approvalRuleSnapshot(WorkspaceApprovalMode.MULTI_STEP);

        PostService postService = createService(
                postCollectionRepo,
                reviewHistoryRepo,
                accountProfileService,
                postCollectionVersionService,
                workspaceCapabilityService,
                workspaceApprovalRuleService
        );

        PostCollectionEntity collection = createApprovedLockedCollection();
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(workspaceApprovalRuleService.getSnapshot("workspace_1")).thenReturn(approvalRuleSnapshot);
        when(workspaceApprovalRuleService.resolveApprovalMode(approvalRuleSnapshot, collection))
                .thenReturn(WorkspaceApprovalMode.MULTI_STEP);

        UpdatePostCollectionRequest request = new UpdatePostCollectionRequest();
        request.setDescription("Updated caption for final approval");

        WorkspaceContext.set("workspace_1", WorkspaceRole.EDITOR);

        assertThatThrownBy(() -> postService.updatePostCollection("user_123", 1L, request))
                .isInstanceOf(SocialRavenException.class)
                .hasMessage("This collection is approval-locked. Confirm the edit to move it back into review.")
                .extracting("errorCode")
                .isEqualTo("409");

        verify(postCollectionRepo, never()).save(any(PostCollectionEntity.class));
        verify(postCollectionVersionService, never()).recordVersion(any(), eq(PostCollectionVersionEvent.REAPPROVAL_REQUIRED), anyString());
    }

    @Test
    void updatePostCollectionMovesApprovedContentBackIntoReviewAfterConfirmedMaterialEdit() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewHistoryRepo reviewHistoryRepo = mock(PostCollectionReviewHistoryRepo.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        WorkspaceApprovalRuleService workspaceApprovalRuleService = mock(WorkspaceApprovalRuleService.class);
        WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot =
                approvalRuleSnapshot(WorkspaceApprovalMode.MULTI_STEP);

        PostService postService = createService(
                postCollectionRepo,
                reviewHistoryRepo,
                accountProfileService,
                postCollectionVersionService,
                workspaceCapabilityService,
                workspaceApprovalRuleService
        );

        PostCollectionEntity collection = createApprovedLockedCollection();
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(postCollectionRepo.save(any(PostCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewHistoryRepo.save(any(PostCollectionReviewHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewHistoryRepo.findAllByPostCollectionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(accountProfileService.getAllConnectedAccounts("workspace_1")).thenReturn(List.of());
        when(postCollectionVersionService.buildVersionHistory(1L)).thenReturn(List.of());
        when(postCollectionVersionService.buildApprovedDiff(any(PostCollectionEntity.class))).thenReturn(null);
        when(workspaceApprovalRuleService.getSnapshot("workspace_1")).thenReturn(approvalRuleSnapshot);
        when(workspaceApprovalRuleService.resolveApprovalMode(approvalRuleSnapshot, collection))
                .thenReturn(WorkspaceApprovalMode.MULTI_STEP);

        UpdatePostCollectionRequest request = new UpdatePostCollectionRequest();
        request.setDescription("Updated caption for final approval");
        request.setAcknowledgeApprovalLock(true);

        WorkspaceContext.set("workspace_1", WorkspaceRole.EDITOR);

        var response = postService.updatePostCollection("user_123", 1L, request);

        ArgumentCaptor<PostCollectionEntity> savedCollectionCaptor = ArgumentCaptor.forClass(PostCollectionEntity.class);
        verify(postCollectionRepo).save(savedCollectionCaptor.capture());
        PostCollectionEntity savedCollection = savedCollectionCaptor.getValue();

        ArgumentCaptor<PostCollectionReviewHistoryEntity> reviewHistoryCaptor =
                ArgumentCaptor.forClass(PostCollectionReviewHistoryEntity.class);
        verify(reviewHistoryRepo).save(reviewHistoryCaptor.capture());
        PostCollectionReviewHistoryEntity reviewHistory = reviewHistoryCaptor.getValue();

        assertThat(savedCollection.isDraft()).isTrue();
        assertThat(savedCollection.getReviewStatus()).isEqualTo(PostReviewStatus.IN_REVIEW);
        assertThat(savedCollection.isApprovalLocked()).isFalse();
        assertThat(savedCollection.getApprovalLockedAt()).isNull();
        assertThat(savedCollection.getApprovedAt()).isNull();
        assertThat(savedCollection.getApprovedBy()).isNull();
        assertThat(savedCollection.getRequiredApprovalSteps()).isEqualTo(2);
        assertThat(savedCollection.getCompletedApprovalSteps()).isZero();
        assertThat(savedCollection.getNextApprovalStage()).isEqualTo(PostApprovalStage.APPROVER);
        assertThat(savedCollection.getApprovalReminderAttemptCount()).isZero();
        assertThat(savedCollection.getLastApprovalReminderSentAt()).isNull();
        assertThat(savedCollection.getNextApprovalReminderAt()).isNotNull();
        assertThat(savedCollection.getDescription()).isEqualTo("Updated caption for final approval");
        assertThat(savedCollection.getPosts()).allMatch(post -> post.getPostStatus() == PostStatus.DRAFT);

        assertThat(reviewHistory.getAction()).isEqualTo(PostReviewAction.REAPPROVAL_REQUIRED);
        assertThat(reviewHistory.getFromStatus()).isEqualTo(PostReviewStatus.APPROVED);
        assertThat(reviewHistory.getToStatus()).isEqualTo(PostReviewStatus.IN_REVIEW);
        assertThat(reviewHistory.getNote()).contains("caption");

        verify(postCollectionVersionService).recordVersion(
                savedCollection,
                PostCollectionVersionEvent.REAPPROVAL_REQUIRED,
                "user_123"
        );

        assertThat(response.getOverallStatus()).isEqualTo("IN_REVIEW");
        assertThat(response.getReviewStatus()).isEqualTo("IN_REVIEW");
        assertThat(response.isApprovalLocked()).isFalse();
        assertThat(response.getRequiredApprovalSteps()).isEqualTo(2);
        assertThat(response.getCompletedApprovalSteps()).isZero();
        assertThat(response.getNextApprovalStage()).isEqualTo("APPROVER");
        assertThat(response.getNextApprovalReminderAt()).isNotNull();
    }

    @Test
    void updatePostCollectionRejectsCampaignApprovalOverrideWithoutApprovalRuleAccess() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewHistoryRepo reviewHistoryRepo = mock(PostCollectionReviewHistoryRepo.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        WorkspaceApprovalRuleService workspaceApprovalRuleService = mock(WorkspaceApprovalRuleService.class);

        PostService postService = createService(
                postCollectionRepo,
                reviewHistoryRepo,
                accountProfileService,
                postCollectionVersionService,
                workspaceCapabilityService,
                workspaceApprovalRuleService
        );

        PostCollectionEntity collection = createDraftCollection();
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(workspaceCapabilityService.hasCapability(
                "workspace_1",
                "user_123",
                WorkspaceRole.EDITOR,
                WorkspaceCapability.MANAGE_APPROVAL_RULES
        )).thenReturn(false);

        UpdatePostCollectionRequest request = new UpdatePostCollectionRequest();
        request.setApprovalModeOverride(WorkspaceApprovalMode.NONE);

        WorkspaceContext.set("workspace_1", WorkspaceRole.EDITOR);

        assertThatThrownBy(() -> postService.updatePostCollection("user_123", 1L, request))
                .isInstanceOf(SocialRavenException.class)
                .hasMessage("Approval overrides require approval-rule management access")
                .extracting("errorCode")
                .isEqualTo("403");

        verify(postCollectionRepo, never()).save(any(PostCollectionEntity.class));
    }

    @Test
    void updatePostCollectionTreatsCampaignApprovalOverrideChangeAsMaterialReapprovalTrigger() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewHistoryRepo reviewHistoryRepo = mock(PostCollectionReviewHistoryRepo.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        WorkspaceApprovalRuleService workspaceApprovalRuleService = mock(WorkspaceApprovalRuleService.class);
        WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot =
                approvalRuleSnapshot(WorkspaceApprovalMode.OPTIONAL);

        PostService postService = createService(
                postCollectionRepo,
                reviewHistoryRepo,
                accountProfileService,
                postCollectionVersionService,
                workspaceCapabilityService,
                workspaceApprovalRuleService
        );

        PostCollectionEntity collection = createApprovedLockedCollection();
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(postCollectionRepo.save(any(PostCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewHistoryRepo.save(any(PostCollectionReviewHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewHistoryRepo.findAllByPostCollectionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(accountProfileService.getAllConnectedAccounts("workspace_1")).thenReturn(List.of());
        when(postCollectionVersionService.buildVersionHistory(1L)).thenReturn(List.of());
        when(postCollectionVersionService.buildApprovedDiff(any(PostCollectionEntity.class))).thenReturn(null);
        when(workspaceApprovalRuleService.getSnapshot("workspace_1")).thenReturn(approvalRuleSnapshot);
        when(workspaceApprovalRuleService.resolveApprovalMode(approvalRuleSnapshot, collection))
                .thenReturn(WorkspaceApprovalMode.MULTI_STEP);
        when(workspaceCapabilityService.hasCapability(
                "workspace_1",
                "user_123",
                WorkspaceRole.EDITOR,
                WorkspaceCapability.MANAGE_APPROVAL_RULES
        )).thenReturn(true);

        UpdatePostCollectionRequest request = new UpdatePostCollectionRequest();
        request.setApprovalModeOverride(WorkspaceApprovalMode.NONE);
        request.setAcknowledgeApprovalLock(true);

        WorkspaceContext.set("workspace_1", WorkspaceRole.EDITOR);

        var response = postService.updatePostCollection("user_123", 1L, request);

        ArgumentCaptor<PostCollectionReviewHistoryEntity> reviewHistoryCaptor =
                ArgumentCaptor.forClass(PostCollectionReviewHistoryEntity.class);
        verify(reviewHistoryRepo).save(reviewHistoryCaptor.capture());

        assertThat(collection.getApprovalModeOverride()).isEqualTo(WorkspaceApprovalMode.NONE);
        assertThat(collection.getReviewStatus()).isEqualTo(PostReviewStatus.IN_REVIEW);
        assertThat(reviewHistoryCaptor.getValue().getNote()).contains("campaign approval override");
        assertThat(response.getApprovalModeOverride()).isEqualTo("NONE");
        assertThat(response.getReviewStatus()).isEqualTo("IN_REVIEW");
    }

    @Test
    void approvePostCollectionLeavesContentApprovedWhenAutoScheduleDisabled() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewHistoryRepo reviewHistoryRepo = mock(PostCollectionReviewHistoryRepo.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        WorkspaceApprovalRuleService workspaceApprovalRuleService = mock(WorkspaceApprovalRuleService.class);
        WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot =
                approvalRuleSnapshot(WorkspaceApprovalMode.REQUIRED, false);

        PostService postService = createService(
                postCollectionRepo,
                reviewHistoryRepo,
                accountProfileService,
                postCollectionVersionService,
                workspaceCapabilityService,
                workspaceApprovalRuleService
        );

        PostCollectionEntity collection = createInReviewCollection();
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(postCollectionRepo.save(any(PostCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewHistoryRepo.save(any(PostCollectionReviewHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewHistoryRepo.findAllByPostCollectionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(accountProfileService.getAllConnectedAccounts("workspace_1")).thenReturn(List.of());
        when(postCollectionVersionService.buildVersionHistory(1L)).thenReturn(List.of());
        when(postCollectionVersionService.buildApprovedDiff(any(PostCollectionEntity.class))).thenReturn(null);
        when(workspaceApprovalRuleService.getSnapshot("workspace_1")).thenReturn(approvalRuleSnapshot);
        when(workspaceApprovalRuleService.resolveApprovalMode(approvalRuleSnapshot, collection))
                .thenReturn(WorkspaceApprovalMode.REQUIRED);
        when(workspaceCapabilityService.hasCapability(
                "workspace_1",
                "approver_1",
                WorkspaceRole.EDITOR,
                WorkspaceCapability.APPROVE_POSTS
        )).thenReturn(true);

        PostCollectionVersionEntity approvedVersion = new PostCollectionVersionEntity();
        approvedVersion.setId(900L);
        when(postCollectionVersionService.recordVersion(collection, PostCollectionVersionEvent.APPROVED, "approver_1"))
                .thenReturn(approvedVersion);

        WorkspaceContext.set("workspace_1", WorkspaceRole.EDITOR);

        var response = postService.approvePostCollection("approver_1", 1L, null);

        assertThat(collection.isDraft()).isTrue();
        assertThat(collection.getReviewStatus()).isEqualTo(PostReviewStatus.APPROVED);
        assertThat(collection.isApprovalLocked()).isTrue();
        assertThat(collection.getPosts()).allMatch(post -> post.getPostStatus() == PostStatus.DRAFT);
        assertThat(response.getOverallStatus()).isEqualTo("APPROVED");
        assertThat(response.getReviewStatus()).isEqualTo("APPROVED");
        verify(postCollectionVersionService).recordVersion(collection, PostCollectionVersionEvent.APPROVED, "approver_1");
    }

    @Test
    void activateApprovedScheduleSchedulesApprovedContentAndRecordsVersionEvent() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewHistoryRepo reviewHistoryRepo = mock(PostCollectionReviewHistoryRepo.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        WorkspaceApprovalRuleService workspaceApprovalRuleService = mock(WorkspaceApprovalRuleService.class);

        PostService postService = createService(
                postCollectionRepo,
                reviewHistoryRepo,
                accountProfileService,
                postCollectionVersionService,
                workspaceCapabilityService,
                workspaceApprovalRuleService
        );

        PostCollectionEntity collection = createApprovedAwaitingScheduleCollection();
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(postCollectionRepo.save(any(PostCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewHistoryRepo.findAllByPostCollectionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(accountProfileService.getAllConnectedAccounts("workspace_1")).thenReturn(List.of());
        when(postCollectionVersionService.buildVersionHistory(1L)).thenReturn(List.of());
        when(postCollectionVersionService.buildApprovedDiff(any(PostCollectionEntity.class))).thenReturn(null);
        WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot =
                approvalRuleSnapshot(WorkspaceApprovalMode.REQUIRED, false);
        when(workspaceApprovalRuleService.getSnapshot("workspace_1")).thenReturn(approvalRuleSnapshot);
        when(workspaceApprovalRuleService.resolveApprovalMode(approvalRuleSnapshot, collection))
                .thenReturn(WorkspaceApprovalMode.REQUIRED);
        when(workspaceCapabilityService.hasCapability(
                "workspace_1",
                "publisher_1",
                WorkspaceRole.EDITOR,
                WorkspaceCapability.PUBLISH_POSTS
        )).thenReturn(true);

        WorkspaceContext.set("workspace_1", WorkspaceRole.EDITOR);

        var response = postService.activateApprovedSchedule("publisher_1", 1L);

        assertThat(collection.isDraft()).isFalse();
        assertThat(collection.getReviewStatus()).isEqualTo(PostReviewStatus.APPROVED);
        assertThat(collection.getPosts()).allMatch(post -> post.getPostStatus() == PostStatus.SCHEDULED);
        assertThat(response.getOverallStatus()).isEqualTo("SCHEDULED");
        verify(postCollectionVersionService).recordVersion(
                collection,
                PostCollectionVersionEvent.SCHEDULED_AFTER_APPROVAL,
                "publisher_1"
        );
    }

    @Test
    void exportApprovalLogSanitizesSpreadsheetFormulaCells() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewHistoryRepo reviewHistoryRepo = mock(PostCollectionReviewHistoryRepo.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        WorkspaceApprovalRuleService workspaceApprovalRuleService = mock(WorkspaceApprovalRuleService.class);
        WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot =
                approvalRuleSnapshot(WorkspaceApprovalMode.REQUIRED, true);

        PostService postService = createService(
                postCollectionRepo,
                reviewHistoryRepo,
                accountProfileService,
                postCollectionVersionService,
                workspaceCapabilityService,
                workspaceApprovalRuleService
        );

        PostCollectionEntity collection = createApprovedLockedCollection();
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(reviewHistoryRepo.findAllByPostCollectionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(systemReminderEvent()));
        when(accountProfileService.getAllConnectedAccounts("workspace_1")).thenReturn(List.of());
        when(postCollectionVersionService.buildVersionHistory(1L)).thenReturn(List.of(
                new PostCollectionVersionResponse(
                        200L,
                        4,
                        PostCollectionVersionEvent.UPDATED.name(),
                        PostActorType.SYSTEM.name(),
                        null,
                        "SocialRaven",
                        OffsetDateTime.parse("2026-04-02T11:00:00Z"),
                        PostReviewStatus.IN_REVIEW.name(),
                        true,
                        OffsetDateTime.parse("2026-04-10T15:00:00Z")
                )
        ));
        when(postCollectionVersionService.buildApprovedDiff(any(PostCollectionEntity.class))).thenReturn(null);
        when(workspaceApprovalRuleService.getSnapshot("workspace_1")).thenReturn(approvalRuleSnapshot);
        when(workspaceApprovalRuleService.resolveApprovalMode(approvalRuleSnapshot, collection))
                .thenReturn(WorkspaceApprovalMode.REQUIRED);

        WorkspaceContext.set("workspace_1", WorkspaceRole.EDITOR);

        String csv = new String(postService.exportApprovalLog("user_123", 1L));

        assertThat(csv).contains("Occurred At (UTC),Category,Event,Actor");
        assertThat(csv).contains("\"'=SUM(1,1)\"");
    }

    private PostService createService(PostCollectionRepo postCollectionRepo,
                                      PostCollectionReviewHistoryRepo reviewHistoryRepo,
                                      AccountProfileService accountProfileService,
                                      PostCollectionVersionService postCollectionVersionService,
                                      WorkspaceCapabilityService workspaceCapabilityService,
                                      WorkspaceApprovalRuleService workspaceApprovalRuleService) {
        PostService postService = new PostService();
        ReflectionTestUtils.setField(postService, "postCollectionRepo", postCollectionRepo);
        ReflectionTestUtils.setField(postService, "postRepo", mock(PostRepo.class));
        ReflectionTestUtils.setField(postService, "postMediaRepo", mock(PostMediaRepo.class));
        ReflectionTestUtils.setField(postService, "postCollectionReviewHistoryRepo", reviewHistoryRepo);
        ReflectionTestUtils.setField(postService, "workspaceCapabilityService", workspaceCapabilityService);
        ReflectionTestUtils.setField(postService, "workspaceApprovalRuleService", workspaceApprovalRuleService);
        ReflectionTestUtils.setField(postService, "accountProfileService", accountProfileService);
        ReflectionTestUtils.setField(postService, "postCollectionVersionService", postCollectionVersionService);
        ReflectionTestUtils.setField(postService, "clerkUserService", mock(ClerkUserService.class));
        JedisPool jedisPool = mock(JedisPool.class);
        when(jedisPool.getResource()).thenReturn(mock(Jedis.class));
        ReflectionTestUtils.setField(postService, "jedisPool", jedisPool);
        ReflectionTestUtils.setField(postService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(postService, "initialApprovalReminderDelayHours", 12L);
        return postService;
    }

    private PostCollectionEntity createApprovedLockedCollection() {
        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setId(1L);
        collection.setCreatedBy("user_123");
        collection.setWorkspaceId("workspace_1");
        collection.setDescription("Approved caption");
        collection.setDraft(false);
        collection.setReviewStatus(PostReviewStatus.APPROVED);
        collection.setReviewSubmittedAt(OffsetDateTime.parse("2026-04-01T09:00:00Z"));
        collection.setReviewSubmittedBy("user_123");
        collection.setApprovedAt(OffsetDateTime.parse("2026-04-01T10:00:00Z"));
        collection.setApprovedBy("owner_456");
        collection.setApprovalLocked(true);
        collection.setApprovalLockedAt(OffsetDateTime.parse("2026-04-01T10:00:00Z"));
        collection.setRequiredApprovalSteps(2);
        collection.setCompletedApprovalSteps(2);
        collection.setVersionSequence(3);
        collection.setLastApprovedVersionId(55L);
        collection.setApprovalReminderAttemptCount(0);
        collection.setPostCollectionType(PostCollectionType.TEXT);
        collection.setScheduledTime(OffsetDateTime.parse("2026-04-10T15:00:00Z"));

        PostEntity post = new PostEntity();
        post.setPostCollection(collection);
        post.setProviderUserId("acct_1");
        post.setPostStatus(PostStatus.SCHEDULED);
        collection.setPosts(new ArrayList<>(List.of(post)));
        collection.setMediaFiles(new ArrayList<>());
        return collection;
    }

    private PostCollectionEntity createApprovedAwaitingScheduleCollection() {
        PostCollectionEntity collection = createApprovedLockedCollection();
        collection.setDraft(true);
        collection.getPosts().forEach(post -> post.setPostStatus(PostStatus.DRAFT));
        return collection;
    }

    private PostCollectionEntity createInReviewCollection() {
        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setId(1L);
        collection.setCreatedBy("user_123");
        collection.setWorkspaceId("workspace_1");
        collection.setDescription("Launch approval");
        collection.setDraft(true);
        collection.setReviewStatus(PostReviewStatus.IN_REVIEW);
        collection.setReviewSubmittedAt(OffsetDateTime.parse("2026-04-01T09:00:00Z"));
        collection.setReviewSubmittedBy("user_123");
        collection.setRequiredApprovalSteps(1);
        collection.setCompletedApprovalSteps(0);
        collection.setNextApprovalStage(PostApprovalStage.APPROVER);
        collection.setPostCollectionType(PostCollectionType.TEXT);
        collection.setScheduledTime(OffsetDateTime.parse("2026-04-10T15:00:00Z"));

        PostEntity post = new PostEntity();
        post.setId(11L);
        post.setPostCollection(collection);
        post.setProviderUserId("acct_1");
        post.setPostStatus(PostStatus.DRAFT);
        collection.setPosts(new ArrayList<>(List.of(post)));
        collection.setMediaFiles(new ArrayList<>());
        return collection;
    }

    private PostCollectionReviewHistoryEntity systemReminderEvent() {
        PostCollectionReviewHistoryEntity history = new PostCollectionReviewHistoryEntity();
        history.setId(700L);
        history.setPostCollectionId(1L);
        history.setAction(PostReviewAction.REMINDER_SENT);
        history.setFromStatus(PostReviewStatus.IN_REVIEW);
        history.setToStatus(PostReviewStatus.IN_REVIEW);
        history.setActorType(PostActorType.SYSTEM);
        history.setActorDisplayName("SocialRaven");
        history.setNote("=SUM(1,1)");
        history.setCreatedAt(OffsetDateTime.parse("2026-04-02T10:00:00Z"));
        return history;
    }

    private PostCollectionEntity createDraftCollection() {
        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setId(1L);
        collection.setCreatedBy("user_123");
        collection.setWorkspaceId("workspace_1");
        collection.setDescription("Draft caption");
        collection.setDraft(true);
        collection.setReviewStatus(PostReviewStatus.DRAFT);
        collection.setPostCollectionType(PostCollectionType.TEXT);
        collection.setMediaFiles(new ArrayList<>());
        collection.setPosts(new ArrayList<>(List.of()));
        return collection;
    }

    private WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot(
            WorkspaceApprovalMode workspaceApprovalMode) {
        return approvalRuleSnapshot(workspaceApprovalMode, true);
    }

    private WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot(
            WorkspaceApprovalMode workspaceApprovalMode,
            boolean autoScheduleAfterApproval) {
        return new WorkspaceApprovalRuleService.ApprovalRuleSnapshot(
                workspaceApprovalMode,
                autoScheduleAfterApproval,
                Map.of(),
                new EnumMap<>(PostCollectionType.class)
        );
    }
}
