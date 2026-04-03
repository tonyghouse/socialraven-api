package com.tonyghouse.socialraven.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.PostActorType;
import com.tonyghouse.socialraven.constant.PostApprovalStage;
import com.tonyghouse.socialraven.constant.PostReviewAction;
import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostCollectionReviewHistoryEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.PostCollectionReviewHistoryRepo;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.service.EmailService;
import com.tonyghouse.socialraven.service.workspace.WorkspaceCapabilityService;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ApprovalReminderSchedulerTest {

    @Test
    void processPendingApprovalRemindersSendsReminderAndRecordsSystemAuditEvent() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewHistoryRepo reviewHistoryRepo = mock(PostCollectionReviewHistoryRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        WorkspaceMemberRepo workspaceMemberRepo = mock(WorkspaceMemberRepo.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);
        EmailService emailService = mock(EmailService.class);

        ApprovalReminderScheduler scheduler = createScheduler(
                postCollectionRepo,
                reviewHistoryRepo,
                workspaceRepo,
                workspaceMemberRepo,
                workspaceCapabilityService,
                clerkUserService,
                emailService
        );

        PostCollectionEntity collection = dueCollection();
        WorkspaceMemberEntity approver = workspaceMember("workspace_1", "approver_1", WorkspaceRole.EDITOR);

        when(postCollectionRepo.findAllByDraftTrueAndReviewStatusAndNextApprovalReminderAtIsNotNullAndNextApprovalReminderAtLessThanEqual(
                eq(PostReviewStatus.IN_REVIEW),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(collection));
        when(postCollectionRepo.save(any(PostCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewHistoryRepo.save(any(PostCollectionReviewHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1")).thenReturn(Optional.of(workspace("workspace_1", "Acme EU + US")));
        when(workspaceMemberRepo.findAllByWorkspaceId("workspace_1")).thenReturn(List.of(approver));
        when(workspaceCapabilityService.getEffectiveCapabilities("workspace_1", "approver_1", WorkspaceRole.EDITOR))
                .thenReturn(EnumSet.of(WorkspaceCapability.APPROVE_POSTS));
        when(clerkUserService.getUserEmail("approver_1")).thenReturn("approver@example.com");

        scheduler.processPendingApprovalReminders();

        verify(emailService).sendApprovalReminderEmail(
                "approver@example.com",
                "Acme EU + US",
                10L,
                "Launch week caption",
                "Approver review",
                "2026-04-12T09:30Z",
                1
        );
        verify(emailService, never()).sendApprovalEscalationEmail(any(), any(), any(), any(), any(), any(), any(int.class));

        assertThat(collection.getApprovalReminderAttemptCount()).isEqualTo(1);
        assertThat(collection.getLastApprovalReminderSentAt()).isNotNull();
        assertThat(collection.getNextApprovalReminderAt()).isAfter(collection.getLastApprovalReminderSentAt());
        assertThat(collection.getApprovalEscalatedAt()).isNull();

        ArgumentCaptor<PostCollectionReviewHistoryEntity> auditCaptor =
                ArgumentCaptor.forClass(PostCollectionReviewHistoryEntity.class);
        verify(reviewHistoryRepo).save(auditCaptor.capture());
        PostCollectionReviewHistoryEntity auditEvent = auditCaptor.getValue();
        assertThat(auditEvent.getAction()).isEqualTo(PostReviewAction.REMINDER_SENT);
        assertThat(auditEvent.getActorType()).isEqualTo(PostActorType.SYSTEM);
        assertThat(auditEvent.getNote()).contains("Reminder #1");
    }

    @Test
    void processPendingApprovalRemindersEscalatesWhenNoApproverEmailCanBeResolved() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewHistoryRepo reviewHistoryRepo = mock(PostCollectionReviewHistoryRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        WorkspaceMemberRepo workspaceMemberRepo = mock(WorkspaceMemberRepo.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);
        EmailService emailService = mock(EmailService.class);

        ApprovalReminderScheduler scheduler = createScheduler(
                postCollectionRepo,
                reviewHistoryRepo,
                workspaceRepo,
                workspaceMemberRepo,
                workspaceCapabilityService,
                clerkUserService,
                emailService
        );

        PostCollectionEntity collection = dueCollection();

        when(postCollectionRepo.findAllByDraftTrueAndReviewStatusAndNextApprovalReminderAtIsNotNullAndNextApprovalReminderAtLessThanEqual(
                eq(PostReviewStatus.IN_REVIEW),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(collection));
        when(postCollectionRepo.save(any(PostCollectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewHistoryRepo.save(any(PostCollectionReviewHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1")).thenReturn(Optional.of(workspace("workspace_1", "Acme EU + US")));
        when(workspaceMemberRepo.findAllByWorkspaceId("workspace_1")).thenReturn(List.of());

        scheduler.processPendingApprovalReminders();

        verify(emailService).sendApprovalEscalationEmail(
                "ops@socialraven.io",
                "Acme EU + US",
                10L,
                "Launch week caption",
                "Approver review",
                "2026-04-12T09:30Z",
                0
        );
        verify(emailService, never()).sendApprovalReminderEmail(any(), any(), any(), any(), any(), any(), any(int.class));

        assertThat(collection.getApprovalEscalatedAt()).isNotNull();
        assertThat(collection.getNextApprovalReminderAt()).isNull();

        ArgumentCaptor<PostCollectionReviewHistoryEntity> auditCaptor =
                ArgumentCaptor.forClass(PostCollectionReviewHistoryEntity.class);
        verify(reviewHistoryRepo).save(auditCaptor.capture());
        PostCollectionReviewHistoryEntity auditEvent = auditCaptor.getValue();
        assertThat(auditEvent.getAction()).isEqualTo(PostReviewAction.ESCALATED);
        assertThat(auditEvent.getActorType()).isEqualTo(PostActorType.SYSTEM);
        assertThat(auditEvent.getNote()).contains("no valid approver email address");
    }

    private ApprovalReminderScheduler createScheduler(PostCollectionRepo postCollectionRepo,
                                                      PostCollectionReviewHistoryRepo reviewHistoryRepo,
                                                      WorkspaceRepo workspaceRepo,
                                                      WorkspaceMemberRepo workspaceMemberRepo,
                                                      WorkspaceCapabilityService workspaceCapabilityService,
                                                      ClerkUserService clerkUserService,
                                                      EmailService emailService) {
        ApprovalReminderScheduler scheduler = new ApprovalReminderScheduler();
        ReflectionTestUtils.setField(scheduler, "postCollectionRepo", postCollectionRepo);
        ReflectionTestUtils.setField(scheduler, "postCollectionReviewHistoryRepo", reviewHistoryRepo);
        ReflectionTestUtils.setField(scheduler, "workspaceRepo", workspaceRepo);
        ReflectionTestUtils.setField(scheduler, "workspaceMemberRepo", workspaceMemberRepo);
        ReflectionTestUtils.setField(scheduler, "workspaceCapabilityService", workspaceCapabilityService);
        ReflectionTestUtils.setField(scheduler, "clerkUserService", clerkUserService);
        ReflectionTestUtils.setField(scheduler, "emailService", emailService);
        ReflectionTestUtils.setField(scheduler, "approvalEscalationAdminEmail", "ops@socialraven.io");
        ReflectionTestUtils.setField(scheduler, "reminderRepeatHours", 12L);
        ReflectionTestUtils.setField(scheduler, "maxReminderAttempts", 3);
        return scheduler;
    }

    private PostCollectionEntity dueCollection() {
        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setId(10L);
        collection.setWorkspaceId("workspace_1");
        collection.setDraft(true);
        collection.setReviewStatus(PostReviewStatus.IN_REVIEW);
        collection.setDescription("Launch week caption");
        collection.setScheduledTime(OffsetDateTime.parse("2026-04-12T09:30:00Z"));
        collection.setApprovalReminderAttemptCount(0);
        collection.setNextApprovalReminderAt(OffsetDateTime.now().minusMinutes(10));
        collection.setNextApprovalStage(PostApprovalStage.APPROVER);
        return collection;
    }

    private WorkspaceMemberEntity workspaceMember(String workspaceId, String userId, WorkspaceRole role) {
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(role);
        member.setJoinedAt(OffsetDateTime.parse("2026-04-01T00:00:00Z"));
        return member;
    }

    private WorkspaceEntity workspace(String workspaceId, String name) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId(workspaceId);
        workspace.setName(name);
        workspace.setCompanyId("company_1");
        return workspace;
    }
}
