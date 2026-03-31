package com.tonyghouse.socialraven.scheduler;

import com.tonyghouse.socialraven.constant.PostStatus;
import com.tonyghouse.socialraven.constant.RecoveryState;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.service.EmailService;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class FailedCollectionRecoveryScheduler {

    private static final int MAX_NOTIFICATION_ATTEMPTS = 10;
    private static final String DEFAULT_FAILURE_SUMMARY =
            "We couldn't publish this collection to one or more selected channels. Review the media, content, or platform-specific settings and create a recovery draft.";

    @Autowired
    private PostCollectionRepo postCollectionRepo;

    @Autowired
    private ClerkUserService clerkUserService;

    @Autowired
    private EmailService emailService;

    @Scheduled(cron = "0 */15 * * * ?", zone = "UTC")
    @Transactional
    public void processFailedCollections() {
        OffsetDateTime now = OffsetDateTime.now();
        processDueRecoveryReminders(now);
    }

    private void processDueRecoveryReminders(OffsetDateTime now) {
        List<PostCollectionEntity> dueCollections =
                postCollectionRepo.findAllNonDraftWithPostsByFailureStateAndNextNotificationAtBefore(
                        RecoveryState.RECOVERY_REQUIRED, now);

        for (PostCollectionEntity collection : dueCollections) {
            if (!hasUnresolvedFailedChannels(collection.getPosts()) || collection.getRecoveryCollectionId() != null) {
                continue;
            }

            try {
                sendUserRecoveryEmailOrEscalate(collection, now);
            } catch (Exception e) {
                log.error("Failed to process recovery reminder for collectionId={}", collection.getId(), e);
            }
        }
    }

    private void sendUserRecoveryEmailOrEscalate(PostCollectionEntity collection, OffsetDateTime now) {
        // TODO: Add quiet-hours delivery before sending user reminders.
        // Use the creator's timezone once available and suppress delivery during an overnight window
        // such as 10 PM to 8 AM local time. If a reminder lands inside quiet hours, move
        // nextNotificationAt to the next allowed send time rather than skipping the reminder entirely.
        // This should also consider workspace-level overrides, paused notifications, and app-notification
        // parity once in-product alerts are introduced.
        if (collection.getFailureDetectedAt() == null) {
            collection.setFailureDetectedAt(now);
        }
        if (collection.getFailureReasonSummary() == null || collection.getFailureReasonSummary().isBlank()) {
            collection.setFailureReasonSummary(DEFAULT_FAILURE_SUMMARY);
        }
        String ownerEmail = clerkUserService.getUserEmail(collection.getCreatedBy());
        if (ownerEmail == null || ownerEmail.isBlank()) {
            log.warn("Collection recovery escalation because owner email is unavailable: collectionId={}, ownerUserId={}",
                    collection.getId(), collection.getCreatedBy());
            escalateToAdmin(collection, ownerEmail, now);
            return;
        }

        int nextAttempt = collection.getNotificationAttemptCount() + 1;
        emailService.sendFailedCollectionRecoveryEmail(
                ownerEmail.trim(),
                collection.getId(),
                collection.getDescription(),
                failureSummary(collection),
                nextAttempt
        );

        collection.setNotificationAttemptCount(nextAttempt);
        collection.setLastNotificationSentAt(now);

        if (nextAttempt >= MAX_NOTIFICATION_ATTEMPTS) {
            escalateToAdmin(collection, ownerEmail, now);
            return;
        }

        collection.setNextNotificationAt(now.plusHours(3));
    }

    private void escalateToAdmin(PostCollectionEntity collection, String ownerEmail, OffsetDateTime now) {
        emailService.sendFailedCollectionEscalationEmail(
                collection.getId(),
                collection.getWorkspaceId(),
                collection.getCreatedBy(),
                ownerEmail,
                collection.getDescription(),
                collection.getNotificationAttemptCount()
        );

        collection.setFailureState(RecoveryState.ESCALATED_TO_ADMIN);
        collection.setAdminEscalatedAt(now);
        collection.setNotificationStoppedAt(now);
        collection.setNextNotificationAt(null);
    }

    private boolean hasUnresolvedFailedChannels(List<PostEntity> posts) {
        return posts != null
                && !posts.isEmpty()
                && posts.stream().anyMatch(post -> post.getPostStatus() == PostStatus.FAILED)
                && posts.stream().noneMatch(post -> post.getPostStatus() == PostStatus.SCHEDULED);
    }

    private String failureSummary(PostCollectionEntity collection) {
        return collection.getFailureReasonSummary() != null && !collection.getFailureReasonSummary().isBlank()
                ? collection.getFailureReasonSummary()
                : DEFAULT_FAILURE_SUMMARY;
    }
}
