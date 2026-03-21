package com.ghouse.socialraven.service.onboarding;

import com.ghouse.socialraven.constant.UserType;
import com.ghouse.socialraven.constant.WorkspaceRole;
import com.ghouse.socialraven.dto.onboarding.CompleteOnboardingRequest;
import com.ghouse.socialraven.dto.onboarding.OnboardingStatusResponse;
import com.ghouse.socialraven.entity.UserProfileEntity;
import com.ghouse.socialraven.entity.WorkspaceEntity;
import com.ghouse.socialraven.entity.WorkspaceMemberEntity;
import com.ghouse.socialraven.entity.WorkspaceSettingsEntity;
import com.ghouse.socialraven.exception.SocialRavenException;
import com.ghouse.socialraven.repo.UserProfileRepo;
import com.ghouse.socialraven.repo.WorkspaceMemberRepo;
import com.ghouse.socialraven.repo.WorkspaceRepo;
import com.ghouse.socialraven.repo.WorkspaceSettingsRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class OnboardingService {

    @Autowired
    private UserProfileRepo userProfileRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private WorkspaceMemberRepo workspaceMemberRepo;

    @Autowired
    private WorkspaceSettingsRepo workspaceSettingsRepo;

    /**
     * Returns onboarding status for the caller.
     * Returns completed=false if no user_profile row exists yet.
     */
    public OnboardingStatusResponse getStatus(String userId) {
        Optional<UserProfileEntity> profile = userProfileRepo.findById(userId);
        if (profile.isEmpty()) {
            return new OnboardingStatusResponse(false, null, null);
        }

        // Return the first workspace the user owns (personal workspace for influencers)
        List<WorkspaceMemberEntity> memberships = workspaceMemberRepo.findAllByUserId(userId);
        String workspaceId = memberships.stream()
                .filter(m -> m.getRole() == WorkspaceRole.OWNER)
                .map(WorkspaceMemberEntity::getWorkspaceId)
                .findFirst()
                .orElse(null);

        return new OnboardingStatusResponse(true, profile.get().getUserType().name(), workspaceId);
    }

    /**
     * Completes onboarding: persists user_profile, creates first workspace, adds OWNER membership.
     * Idempotent — returns existing status if already completed.
     */
    @Transactional
    public OnboardingStatusResponse completeOnboarding(String userId, CompleteOnboardingRequest request) {
        if (userProfileRepo.existsById(userId)) {
            log.info("Onboarding already completed for user {}", userId);
            return getStatus(userId);
        }

        if (request.getUserType() == null) {
            throw new SocialRavenException("userType is required", HttpStatus.BAD_REQUEST);
        }

        UserType userType = request.getUserType();

        if (userType == UserType.AGENCY &&
                (request.getWorkspaceName() == null || request.getWorkspaceName().isBlank())) {
            throw new SocialRavenException("workspaceName is required for AGENCY accounts", HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 1. Persist user profile
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setUserType(userType);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        userProfileRepo.save(profile);

        // 2. Create first workspace
        String workspaceId;
        String workspaceName;

        if (userType == UserType.INFLUENCER) {
            workspaceId = "personal_" + userId;
            workspaceName = "main";
        } else {
            workspaceId = UUID.randomUUID().toString();
            workspaceName = request.getWorkspaceName().trim();
        }

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId(workspaceId);
        workspace.setName(workspaceName);
        workspace.setCompanyName(request.getCompanyName() != null ? request.getCompanyName().trim() : null);
        workspace.setOwnerUserId(userId);
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        workspaceRepo.save(workspace);

        // 3. Add OWNER membership
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(WorkspaceRole.OWNER);
        member.setJoinedAt(now);
        workspaceMemberRepo.save(member);

        // 4. Create default workspace settings
        WorkspaceSettingsEntity settings = new WorkspaceSettingsEntity();
        settings.setWorkspaceId(workspaceId);
        settings.setDefaultTz("UTC");
        settings.setUpdatedAt(now);
        workspaceSettingsRepo.save(settings);

        log.info("Onboarding completed for user {} as {} with workspace {}", userId, userType, workspaceId);
        return new OnboardingStatusResponse(true, userType.name(), workspaceId);
    }

    /**
     * Upgrades an INFLUENCER to AGENCY.
     * Existing workspace is kept; agency features unlock immediately.
     */
    @Transactional
    public OnboardingStatusResponse upgradeToAgency(String userId) {
        UserProfileEntity profile = userProfileRepo.findById(userId)
                .orElseThrow(() -> new SocialRavenException("Onboarding not completed", HttpStatus.BAD_REQUEST));

        if (profile.getUserType() == UserType.AGENCY) {
            return getStatus(userId);
        }

        profile.setUserType(UserType.AGENCY);
        profile.setUpdatedAt(OffsetDateTime.now());
        userProfileRepo.save(profile);

        log.info("User {} upgraded from INFLUENCER to AGENCY", userId);
        return getStatus(userId);
    }
}
