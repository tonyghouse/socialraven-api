package com.tonyghouse.socialraven.scheduler;

import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.repo.AccountAnalyticsSnapshotRepo;
import com.tonyghouse.socialraven.repo.AnalyticsJobRepo;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.repo.PostAnalyticsSnapshotRepo;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.UserPlanRepo;
import com.tonyghouse.socialraven.repo.WorkspaceInvitationRepo;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.repo.WorkspaceSettingsRepo;
import com.tonyghouse.socialraven.service.cache.RequestAccessCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GDPR §5.6 — workspace deletion with 30-day retention.
 *
 * Workspaces are soft-deleted (deleted_at set) by WorkspaceService.deleteWorkspace().
 * This scheduler runs daily and permanently erases workspaces whose 30-day window has expired,
 * cascading all member data (posts, oauth tokens, analytics, members, invitations).
 */
@Component
@Slf4j
public class WorkspaceDeletionScheduler {

    private static final int RETENTION_DAYS = 30;

    @Autowired private WorkspaceRepo workspaceRepo;
    @Autowired private WorkspaceMemberRepo workspaceMemberRepo;
    @Autowired private WorkspaceInvitationRepo workspaceInvitationRepo;
    @Autowired private WorkspaceSettingsRepo workspaceSettingsRepo;
    @Autowired private PostCollectionRepo postCollectionRepo;
    @Autowired private OAuthInfoRepo oauthInfoRepo;
    @Autowired private UserPlanRepo userPlanRepo;
    @Autowired private AnalyticsJobRepo analyticsJobRepo;
    @Autowired private PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo;
    @Autowired private AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo;
    @Autowired private RequestAccessCacheService requestAccessCacheService;

    @Scheduled(cron = "0 0 3 * * ?", zone = "UTC")  // 03:00 UTC daily
    @Transactional
    public void hardDeleteExpiredWorkspaces() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RETENTION_DAYS);
        List<WorkspaceEntity> expired = workspaceRepo.findAllByDeletedAtIsNotNullAndDeletedAtBefore(cutoff);

        if (expired.isEmpty()) {
            return;
        }

        log.info("WorkspaceDeletionScheduler: hard-deleting {} workspace(s) past {}-day retention", expired.size(), RETENTION_DAYS);

        for (WorkspaceEntity workspace : expired) {
            String workspaceId = workspace.getId();
            try {
                hardDelete(workspaceId);
                log.info("Workspace hard-deleted (GDPR): id={}", workspaceId);
            } catch (Exception e) {
                log.error("Failed to hard-delete workspace id={}: {}", workspaceId, e.getMessage(), e);
                // Continue with remaining workspaces — don't let one failure block others
            }
        }
    }

    /**
     * Deletes all data for a workspace in FK-safe order, then removes the workspace row.
     * Order: analytics → posts/media (JPA cascade) → oauth → billing → invitations → settings → members → workspace
     */
    private void hardDelete(String workspaceId) {
        requestAccessCacheService.evictWorkspaceRolesForWorkspace(workspaceId);

        // 1. Analytics snapshots and jobs (reference post_id / workspace_id)
        postAnalyticsSnapshotRepo.deleteAllByWorkspaceId(workspaceId);
        accountAnalyticsSnapshotRepo.deleteAllByWorkspaceId(workspaceId);
        analyticsJobRepo.deleteAllByWorkspaceId(workspaceId);

        // 2. Posts and media — loaded so JPA CascadeType.ALL fires on PostCollectionEntity
        List<PostCollectionEntity> collections = postCollectionRepo.findAllByWorkspaceId(workspaceId);
        postCollectionRepo.deleteAll(collections);

        // 3. OAuth tokens
        oauthInfoRepo.deleteAllByWorkspaceId(workspaceId);

        // 4. Billing plan
        userPlanRepo.deleteAllByWorkspaceId(workspaceId);

        // 5. Invitations
        workspaceInvitationRepo.deleteAllByWorkspaceId(workspaceId);

        // 6. Workspace settings
        workspaceSettingsRepo.deleteById(workspaceId);

        // 7. Members
        workspaceMemberRepo.deleteAllByWorkspaceId(workspaceId);

        // 8. Workspace row itself
        workspaceRepo.deleteById(workspaceId);
    }
}
