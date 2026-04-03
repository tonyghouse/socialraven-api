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
import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.constant.PostStatus;
import com.tonyghouse.socialraven.constant.WorkspaceApprovalMode;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.UpdatePostCollectionRequest;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostCollectionReviewHistoryEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.PostCollectionReviewHistoryRepo;
import com.tonyghouse.socialraven.repo.PostMediaRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import com.tonyghouse.socialraven.service.workspace.WorkspaceCapabilityService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PostServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void updatePostCollectionRequiresExplicitConfirmationForApprovalLockedMaterialEdits() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewHistoryRepo reviewHistoryRepo = mock(PostCollectionReviewHistoryRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);

        PostService postService = createService(
                postCollectionRepo,
                reviewHistoryRepo,
                workspaceRepo,
                accountProfileService,
                postCollectionVersionService
        );

        PostCollectionEntity collection = createApprovedLockedCollection();
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1")).thenReturn(Optional.of(workspace("workspace_1", WorkspaceApprovalMode.MULTI_STEP)));

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
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);

        PostService postService = createService(
                postCollectionRepo,
                reviewHistoryRepo,
                workspaceRepo,
                accountProfileService,
                postCollectionVersionService
        );

        PostCollectionEntity collection = createApprovedLockedCollection();
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(postCollectionRepo.save(any(PostCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewHistoryRepo.save(any(PostCollectionReviewHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewHistoryRepo.findAllByPostCollectionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1")).thenReturn(Optional.of(workspace("workspace_1", WorkspaceApprovalMode.MULTI_STEP)));
        when(accountProfileService.getAllConnectedAccounts("workspace_1")).thenReturn(List.of());
        when(postCollectionVersionService.buildVersionHistory(1L)).thenReturn(List.of());
        when(postCollectionVersionService.buildApprovedDiff(any(PostCollectionEntity.class))).thenReturn(null);

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

    private PostService createService(PostCollectionRepo postCollectionRepo,
                                      PostCollectionReviewHistoryRepo reviewHistoryRepo,
                                      WorkspaceRepo workspaceRepo,
                                      AccountProfileService accountProfileService,
                                      PostCollectionVersionService postCollectionVersionService) {
        PostService postService = new PostService();
        ReflectionTestUtils.setField(postService, "postCollectionRepo", postCollectionRepo);
        ReflectionTestUtils.setField(postService, "postRepo", mock(PostRepo.class));
        ReflectionTestUtils.setField(postService, "postMediaRepo", mock(PostMediaRepo.class));
        ReflectionTestUtils.setField(postService, "postCollectionReviewHistoryRepo", reviewHistoryRepo);
        ReflectionTestUtils.setField(postService, "workspaceRepo", workspaceRepo);
        ReflectionTestUtils.setField(postService, "workspaceCapabilityService", mock(WorkspaceCapabilityService.class));
        ReflectionTestUtils.setField(postService, "accountProfileService", accountProfileService);
        ReflectionTestUtils.setField(postService, "postCollectionVersionService", postCollectionVersionService);
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

    private WorkspaceEntity workspace(String workspaceId, WorkspaceApprovalMode approvalMode) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId(workspaceId);
        workspace.setName("Agency Workspace");
        workspace.setCompanyId("company_1");
        workspace.setApprovalMode(approvalMode);
        return workspace;
    }
}
