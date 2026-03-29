package com.tonyghouse.socialraven.service.workspace;

import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.workspace.CreateWorkspaceRequest;
import com.tonyghouse.socialraven.dto.workspace.UpdateWorkspaceRequest;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceResponse;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.entity.PlanConfigEntity;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.UserProfileEntity;
import com.tonyghouse.socialraven.entity.UserPlanEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.helper.PostPoolHelper;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.repo.PlanConfigRepo;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.UserProfileRepo;
import com.tonyghouse.socialraven.repo.UserPlanRepo;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.scheduler.PostRedisService;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WorkspaceService {
    private static final String OAUTH_EXPIRY_POOL_KEY = "oauth-expiry-pool";

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private WorkspaceMemberRepo workspaceMemberRepo;

    @Autowired
    private UserPlanRepo userPlanRepo;

    @Autowired
    private PlanConfigRepo planConfigRepo;

    @Autowired
    private UserProfileRepo userProfileRepo;

    @Autowired
    private PostCollectionRepo postCollectionRepo;

    @Autowired
    private OAuthInfoRepo oauthInfoRepo;

    @Autowired
    private PostRedisService postRedisService;

    @Autowired
    private ClerkUserService clerkUserService;

    @Autowired
    private EmailService emailService;

    /**
     * Returns all workspaces the caller belongs to, with their role in each.
     */
    public List<WorkspaceResponse> getMyWorkspaces(String userId) {
        List<WorkspaceMemberEntity> memberships = workspaceMemberRepo.findAllByUserId(userId);
        return memberships.stream()
                .map(m -> {
                    // Only return active (not soft-deleted) workspaces
                    WorkspaceEntity ws = workspaceRepo.findByIdAndDeletedAtIsNull(m.getWorkspaceId())
                            .orElse(null);
                    if (ws == null) return null;
                    return toResponse(ws, m.getRole());
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    public List<WorkspaceResponse> getDeletedWorkspaces(String userId) {
        return workspaceMemberRepo.findAllByUserId(userId).stream()
                .filter(member -> member.getRole() == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.ADMIN)
                .map(member -> workspaceRepo.findById(member.getWorkspaceId())
                        .filter(workspace -> workspace.getDeletedAt() != null)
                        .map(workspace -> toResponse(workspace, member.getRole()))
                        .orElse(null))
                .filter(response -> response != null)
                .sorted((a, b) -> b.getDeletedAt().compareTo(a.getDeletedAt()))
                .collect(Collectors.toList());
    }

    /**
     * Creates a new workspace for the caller.
     * Plan-gated: checks max_workspaces against how many workspaces the user already owns.
     */
    @Transactional
    public WorkspaceResponse createWorkspace(String userId, CreateWorkspaceRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new SocialRavenException("Workspace name is required", HttpStatus.BAD_REQUEST);
        }

        UserProfileEntity profile = userProfileRepo.findById(userId)
                .orElseThrow(() -> new SocialRavenException("Onboarding must be completed before creating a workspace", HttpStatus.FORBIDDEN));
        if (!profile.isCanCreateWorkspaces()) {
            throw new SocialRavenException("Only owners and admins can create workspaces", HttpStatus.FORBIDDEN);
        }

        // Plan-gate: count current active owned workspaces vs allowed limit
        UserPlanEntity planEntity = userPlanRepo.findByUserId(userId).orElse(null);
        if (planEntity != null) {
            PlanConfigEntity config = planConfigRepo.findById(planEntity.getPlanType()).orElse(null);
            if (config != null && config.getMaxWorkspaces() > 0) {
                long ownedCount = workspaceRepo.findAllByOwnerUserIdAndDeletedAtIsNull(userId).size();
                if (ownedCount >= config.getMaxWorkspaces()) {
                    throw new SocialRavenException(
                            "Workspace limit reached for your plan (" + config.getMaxWorkspaces() + ")",
                            HttpStatus.FORBIDDEN);
                }
            }
        }

        String workspaceId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId(workspaceId);
        workspace.setName(req.getName().trim());
        workspace.setCompanyName(req.getCompanyName());
        workspace.setLogoS3Key(req.getLogoS3Key());
        workspace.setOwnerUserId(userId);
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        workspaceRepo.save(workspace);

        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(WorkspaceRole.OWNER);
        member.setJoinedAt(now);
        workspaceMemberRepo.save(member);

        log.info("Workspace created: id={}, owner={}", workspaceId, userId);
        return toResponse(workspace, WorkspaceRole.OWNER);
    }

    /**
     * Returns workspace details. Caller must be a member (enforced by WorkspaceAccessFilter).
     */
    public WorkspaceResponse getWorkspace(String workspaceId, String userId) {
        WorkspaceEntity workspace = workspaceRepo.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));
        WorkspaceRole role = workspaceMemberRepo.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMemberEntity::getRole)
                .orElseThrow(() -> new SocialRavenException("Access denied", HttpStatus.FORBIDDEN));
        return toResponse(workspace, role);
    }

    /**
     * Updates workspace details. Caller must be ADMIN or OWNER.
     */
    @Transactional
    public WorkspaceResponse updateWorkspace(String workspaceId, String userId, UpdateWorkspaceRequest req) {
        WorkspaceEntity workspace = workspaceRepo.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));

        WorkspaceRole role = workspaceMemberRepo.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMemberEntity::getRole)
                .orElseThrow(() -> new SocialRavenException("Access denied", HttpStatus.FORBIDDEN));

        if (role != WorkspaceRole.OWNER && role != WorkspaceRole.ADMIN) {
            throw new SocialRavenException("ADMIN or OWNER role required", HttpStatus.FORBIDDEN);
        }

        if (req.getName() != null && !req.getName().isBlank()) {
            workspace.setName(req.getName().trim());
        }
        if (req.getCompanyName() != null) {
            workspace.setCompanyName(req.getCompanyName());
        }
        if (req.getLogoS3Key() != null) {
            workspace.setLogoS3Key(req.getLogoS3Key());
        }
        workspace.setUpdatedAt(OffsetDateTime.now());
        workspaceRepo.save(workspace);

        return toResponse(workspace, role);
    }

    /**
     * Soft-deletes a workspace. Caller must be ADMIN or OWNER.
     * Sets deleted_at = now(). WorkspaceDeletionScheduler hard-deletes after 30 days (GDPR §5.6).
     */
    @Transactional
    public void deleteWorkspace(String workspaceId, String userId) {
        WorkspaceEntity workspace = workspaceRepo.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));

        WorkspaceRole role = workspaceMemberRepo.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMemberEntity::getRole)
                .orElseThrow(() -> new SocialRavenException("Access denied", HttpStatus.FORBIDDEN));

        if (role != WorkspaceRole.OWNER && role != WorkspaceRole.ADMIN) {
            throw new SocialRavenException("ADMIN or OWNER role required", HttpStatus.FORBIDDEN);
        }

        if (workspaceId.startsWith("personal_")) {
            throw new SocialRavenException("Personal workspace cannot be deleted", HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now();
        workspace.setDeletedAt(now);
        workspace.setUpdatedAt(now);
        workspaceRepo.save(workspace);
        disableWorkspaceQueues(workspaceId);
        notifyWorkspaceMembers(workspaceId, workspace.getName(), userId, true);
        log.info("Workspace soft-deleted (30-day retention): id={}, by userId={}", workspaceId, userId);
    }

    @Transactional
    public WorkspaceResponse restoreWorkspace(String workspaceId, String userId) {
        WorkspaceEntity workspace = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));

        WorkspaceRole role = workspaceMemberRepo.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMemberEntity::getRole)
                .orElseThrow(() -> new SocialRavenException("Access denied", HttpStatus.FORBIDDEN));

        if (role != WorkspaceRole.OWNER && role != WorkspaceRole.ADMIN) {
            throw new SocialRavenException("ADMIN or OWNER role required", HttpStatus.FORBIDDEN);
        }

        if (workspace.getDeletedAt() == null) {
            throw new SocialRavenException("Workspace is not deleted", HttpStatus.BAD_REQUEST);
        }

        workspace.setDeletedAt(null);
        workspace.setUpdatedAt(OffsetDateTime.now());
        workspaceRepo.save(workspace);
        restoreWorkspaceQueues(workspaceId);
        notifyWorkspaceMembers(workspaceId, workspace.getName(), userId, false);
        log.info("Workspace restored: id={}, by userId={}", workspaceId, userId);
        return toResponse(workspace, role);
    }

    private void disableWorkspaceQueues(String workspaceId) {
        List<String> scheduledPostIds = postCollectionRepo.findAllByWorkspaceId(workspaceId).stream()
                .flatMap(collection -> collection.getPosts() == null ? java.util.stream.Stream.<PostEntity>empty() : collection.getPosts().stream())
                .filter(post -> post.getPostStatus() == com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED)
                .map(PostEntity::getId)
                .filter(id -> id != null)
                .map(String::valueOf)
                .collect(Collectors.toList());
        postRedisService.removeIds(PostPoolHelper.getPostsPoolName(), scheduledPostIds);

        List<String> oauthIds = oauthInfoRepo.findAllByWorkspaceId(workspaceId).stream()
                .map(OAuthInfoEntity::getId)
                .filter(id -> id != null)
                .map(String::valueOf)
                .collect(Collectors.toList());
        postRedisService.removeIds(OAUTH_EXPIRY_POOL_KEY, oauthIds);
    }

    private void restoreWorkspaceQueues(String workspaceId) {
        Map<String, Double> scheduledPosts = new LinkedHashMap<>();
        for (PostCollectionEntity collection : postCollectionRepo.findAllByWorkspaceId(workspaceId)) {
            if (collection.getPosts() == null) {
                continue;
            }
            for (PostEntity post : collection.getPosts()) {
                if (post.getId() == null
                        || post.getPostStatus() != com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED
                        || post.getScheduledTime() == null) {
                    continue;
                }
                scheduledPosts.put(String.valueOf(post.getId()), (double) post.getScheduledTime().toInstant().toEpochMilli());
            }
        }
        postRedisService.addIds(PostPoolHelper.getPostsPoolName(), scheduledPosts);

        Map<String, Double> oauthIds = new LinkedHashMap<>();
        for (OAuthInfoEntity oauthInfo : oauthInfoRepo.findAllByWorkspaceId(workspaceId)) {
            if (oauthInfo.getId() == null || oauthInfo.getExpiresAt() == null) {
                continue;
            }
            oauthIds.put(String.valueOf(oauthInfo.getId()), oauthInfo.getExpiresAt().doubleValue());
        }
        postRedisService.addIds(OAUTH_EXPIRY_POOL_KEY, oauthIds);
    }

    private void notifyWorkspaceMembers(String workspaceId,
                                        String workspaceName,
                                        String actorUserId,
                                        boolean deleted) {
        String actorName = resolveDisplayName(actorUserId);
        Set<String> emails = new LinkedHashSet<>();

        for (WorkspaceMemberEntity member : workspaceMemberRepo.findAllByWorkspaceId(workspaceId)) {
            ClerkUserService.UserProfile profile = clerkUserService.getUserProfile(member.getUserId());
            if (profile == null || profile.email() == null || profile.email().isBlank()) {
                continue;
            }
            emails.add(profile.email().trim().toLowerCase());
        }

        for (String email : emails) {
            try {
                if (deleted) {
                    emailService.sendWorkspaceDeletedEmail(email, workspaceName, actorName);
                } else {
                    emailService.sendWorkspaceRestoredEmail(email, workspaceName, actorName);
                }
            } catch (Exception e) {
                log.warn("Workspace lifecycle email failed to {}: {}", email, e.getMessage());
            }
        }
    }

    private String resolveDisplayName(String userId) {
        ClerkUserService.UserProfile profile = clerkUserService.getUserProfile(userId);
        if (profile == null) {
            return userId;
        }

        String first = profile.firstName() != null ? profile.firstName().trim() : "";
        String last = profile.lastName() != null ? profile.lastName().trim() : "";
        String fullName = (first + " " + last).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }

        if (profile.email() != null && !profile.email().isBlank()) {
            return profile.email().trim();
        }

        return userId;
    }

    private WorkspaceResponse toResponse(WorkspaceEntity ws, WorkspaceRole role) {
        return new WorkspaceResponse(
                ws.getId(),
                ws.getName(),
                ws.getCompanyName(),
                ws.getOwnerUserId(),
                ws.getLogoS3Key(),
                role,
                ws.getCreatedAt(),
                ws.getUpdatedAt(),
                ws.getDeletedAt()
        );
    }
}
