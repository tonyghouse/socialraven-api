package com.tonyghouse.socialraven.scheduler;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class ApprovalReminderScheduler {

    @Autowired
    private PostCollectionRepo postCollectionRepo;

    @Autowired
    private PostCollectionReviewHistoryRepo postCollectionReviewHistoryRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private WorkspaceMemberRepo workspaceMemberRepo;

    @Autowired
    private WorkspaceCapabilityService workspaceCapabilityService;

    @Autowired
    private ClerkUserService clerkUserService;

    @Autowired
    private EmailService emailService;

    @Value("${socialraven.approval.escalation.admin-email:socialravenapp@gmail.com}")
    private String approvalEscalationAdminEmail;

    @Value("${socialraven.approval.reminder.repeat-hours:12}")
    private long reminderRepeatHours;

    @Value("${socialraven.approval.reminder.max-attempts:3}")
    private int maxReminderAttempts;

    @Scheduled(cron = "0 */30 * * * ?", zone = "UTC")
    @Transactional
    public void processPendingApprovalReminders() {
        OffsetDateTime now = OffsetDateTime.now();
        List<PostCollectionEntity> dueCollections =
                postCollectionRepo.findAllByDraftTrueAndReviewStatusAndNextApprovalReminderAtIsNotNullAndNextApprovalReminderAtLessThanEqual(
                        PostReviewStatus.IN_REVIEW,
                        now
                );

        for (PostCollectionEntity collection : dueCollections) {
            if (!collection.isDraft()
                    || collection.getReviewStatus() != PostReviewStatus.IN_REVIEW
                    || collection.getApprovalEscalatedAt() != null) {
                continue;
            }

            try {
                processCollection(collection, now);
            } catch (Exception e) {
                log.error("Failed to process approval reminder for collectionId={}", collection.getId(), e);
            }
        }
    }

    private void processCollection(PostCollectionEntity collection, OffsetDateTime now) {
        String workspaceName = resolveWorkspaceName(collection.getWorkspaceId());
        String approvalStageLabel = approvalStageLabel(collection.getNextApprovalStage());
        List<String> reminderRecipients = resolveReminderRecipients(collection);
        int nextReminderAttempt = collection.getApprovalReminderAttemptCount() + 1;

        if (reminderRecipients.isEmpty() || nextReminderAttempt > maxReminderAttempts) {
            escalateCollection(collection, workspaceName, approvalStageLabel, now, reminderRecipients.isEmpty());
            return;
        }

        String scheduledTimeDisplay = collection.getScheduledTime() != null
                ? collection.getScheduledTime().toString()
                : "Not scheduled";
        for (String email : reminderRecipients) {
            emailService.sendApprovalReminderEmail(
                    email,
                    workspaceName,
                    collection.getId(),
                    collection.getDescription(),
                    approvalStageLabel,
                    scheduledTimeDisplay,
                    nextReminderAttempt
            );
        }

        collection.setApprovalReminderAttemptCount(nextReminderAttempt);
        collection.setLastApprovalReminderSentAt(now);
        collection.setNextApprovalReminderAt(now.plusHours(reminderRepeatHours));
        postCollectionRepo.save(collection);

        recordSystemAuditEvent(
                collection.getId(),
                PostReviewAction.REMINDER_SENT,
                "Reminder #" + nextReminderAttempt + " sent for " + approvalStageLabel.toLowerCase()
                        + " to " + reminderRecipients.size() + " recipient(s)."
        );
    }

    private void escalateCollection(PostCollectionEntity collection,
                                    String workspaceName,
                                    String approvalStageLabel,
                                    OffsetDateTime now,
                                    boolean missingApproverEmail) {
        List<String> escalationRecipients = resolveEscalationRecipients(collection.getWorkspaceId());
        String scheduledTimeDisplay = collection.getScheduledTime() != null
                ? collection.getScheduledTime().toString()
                : "Not scheduled";

        for (String email : escalationRecipients) {
            emailService.sendApprovalEscalationEmail(
                    email,
                    workspaceName,
                    collection.getId(),
                    collection.getDescription(),
                    approvalStageLabel,
                    scheduledTimeDisplay,
                    collection.getApprovalReminderAttemptCount()
            );
        }

        collection.setApprovalEscalatedAt(now);
        collection.setNextApprovalReminderAt(null);
        postCollectionRepo.save(collection);

        String note = missingApproverEmail
                ? "Approval escalation sent because no valid approver email address could be resolved."
                : "Approval escalation sent after " + collection.getApprovalReminderAttemptCount() + " reminder(s).";
        recordSystemAuditEvent(collection.getId(), PostReviewAction.ESCALATED, note);
    }

    private List<String> resolveReminderRecipients(PostCollectionEntity collection) {
        List<WorkspaceMemberEntity> members = workspaceMemberRepo.findAllByWorkspaceId(collection.getWorkspaceId());
        Set<String> emails = new LinkedHashSet<>();

        for (WorkspaceMemberEntity member : members) {
            if (!isReminderRecipient(collection.getWorkspaceId(), member, collection.getNextApprovalStage())) {
                continue;
            }
            addUserEmail(emails, member.getUserId());
        }

        return List.copyOf(emails);
    }

    private List<String> resolveEscalationRecipients(String workspaceId) {
        List<WorkspaceMemberEntity> members = workspaceMemberRepo.findAllByWorkspaceId(workspaceId);
        Set<String> emails = new LinkedHashSet<>();

        for (WorkspaceMemberEntity member : members) {
            if (member.getRole() == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.ADMIN) {
                addUserEmail(emails, member.getUserId());
            }
        }

        if (approvalEscalationAdminEmail != null && !approvalEscalationAdminEmail.isBlank()) {
            emails.add(approvalEscalationAdminEmail.trim().toLowerCase());
        }

        return List.copyOf(emails);
    }

    private boolean isReminderRecipient(String workspaceId,
                                        WorkspaceMemberEntity member,
                                        PostApprovalStage nextApprovalStage) {
        if (nextApprovalStage == PostApprovalStage.OWNER_FINAL) {
            return member.getRole() == WorkspaceRole.OWNER;
        }
        return workspaceCapabilityService.getEffectiveCapabilities(
                workspaceId,
                member.getUserId(),
                member.getRole()
        ).contains(WorkspaceCapability.APPROVE_POSTS);
    }

    private void addUserEmail(Set<String> emails, String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String email = clerkUserService.getUserEmail(userId);
        if (email != null && !email.isBlank()) {
            emails.add(email.trim().toLowerCase());
        }
    }

    private String resolveWorkspaceName(String workspaceId) {
        return workspaceRepo.findByIdAndDeletedAtIsNull(workspaceId)
                .map(WorkspaceEntity::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(workspaceId);
    }

    private String approvalStageLabel(PostApprovalStage stage) {
        return stage == PostApprovalStage.OWNER_FINAL
                ? "Owner final sign-off"
                : "Approver review";
    }

    private void recordSystemAuditEvent(Long collectionId, PostReviewAction action, String note) {
        if (collectionId == null) {
            return;
        }

        PostCollectionReviewHistoryEntity historyEntity = new PostCollectionReviewHistoryEntity();
        historyEntity.setPostCollectionId(collectionId);
        historyEntity.setAction(action);
        historyEntity.setFromStatus(PostReviewStatus.IN_REVIEW);
        historyEntity.setToStatus(PostReviewStatus.IN_REVIEW);
        historyEntity.setActorType(PostActorType.SYSTEM);
        historyEntity.setActorUserId(null);
        historyEntity.setActorDisplayName("SocialRaven");
        historyEntity.setActorEmail(null);
        historyEntity.setNote(note);
        historyEntity.setCreatedAt(OffsetDateTime.now());
        postCollectionReviewHistoryRepo.save(historyEntity);
    }
}
