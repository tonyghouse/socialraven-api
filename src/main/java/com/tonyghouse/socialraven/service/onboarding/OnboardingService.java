package com.tonyghouse.socialraven.service.onboarding;

import com.tonyghouse.socialraven.constant.UserStatus;
import com.tonyghouse.socialraven.constant.UserType;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.onboarding.CompleteOnboardingRequest;
import com.tonyghouse.socialraven.dto.onboarding.OnboardingStatusResponse;
import com.tonyghouse.socialraven.entity.UserProfileEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.entity.WorkspaceSettingsEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.UserProfileRepo;
import com.tonyghouse.socialraven.repo.WorkspaceInvitationRepo;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.repo.WorkspaceSettingsRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.service.cache.RequestAccessCacheService;
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

    @Autowired
    private WorkspaceInvitationRepo workspaceInvitationRepo;

    @Autowired
    private ClerkUserService clerkUserService;

    @Autowired
    private RequestAccessCacheService requestAccessCacheService;

    /**
     * Returns onboarding status for the caller.
     * Returns completed=false if no user_profile row exists yet.
     */
    public OnboardingStatusResponse getStatus(String userId) {
        Optional<UserProfileEntity> profile = userProfileRepo.findById(userId);
        List<WorkspaceMemberEntity> memberships = workspaceMemberRepo.findAllByUserId(userId);

        if (profile.isEmpty()) {
            boolean hasAcceptedInviteHistory = false;
            String email = clerkUserService.getUserEmail(userId);
            if (email != null && !email.isBlank()) {
                hasAcceptedInviteHistory = workspaceInvitationRepo.existsByInvitedEmailAndAcceptedAtIsNotNull(
                        email.trim().toLowerCase());
            }

            if (memberships.isEmpty() && !hasAcceptedInviteHistory) {
                return new OnboardingStatusResponse(false, null, null, false);
            }

            UserProfileEntity recoveredProfile = new UserProfileEntity();
            recoveredProfile.setUserId(userId);
            recoveredProfile.setUserType(UserType.INFLUENCER);
            recoveredProfile.setStatus(UserStatus.ACTIVE);
            recoveredProfile.setCanCreateWorkspaces(false);
            recoveredProfile.setCreatedAt(OffsetDateTime.now());
            recoveredProfile.setUpdatedAt(OffsetDateTime.now());
            userProfileRepo.save(recoveredProfile);
            requestAccessCacheService.cacheUserStatus(userId, UserStatus.ACTIVE);
            profile = Optional.of(recoveredProfile);
            log.info("Recovered missing user profile for invited teammate: userId={}", userId);
        }

        // Return the first owned workspace when present; otherwise any active membership.
        String workspaceId = memberships.stream()
                .filter(m -> m.getRole() == WorkspaceRole.OWNER)
                .map(WorkspaceMemberEntity::getWorkspaceId)
                .findFirst()
                .or(() -> memberships.stream()
                        .map(WorkspaceMemberEntity::getWorkspaceId)
                        .findFirst())
                .orElse(null);

        return new OnboardingStatusResponse(
                true,
                profile.get().getUserType().name(),
                workspaceId,
                profile.get().isCanCreateWorkspaces()
        );
    }

    /**
     * Completes onboarding: persists user_profile, creates first workspace, adds OWNER membership.
     * Idempotent — returns existing status if already completed.
     */
    @Transactional
    public OnboardingStatusResponse completeOnboarding(String userId, CompleteOnboardingRequest request) {
        boolean isReactivation = false;

        if (userProfileRepo.existsById(userId)) {
            UserProfileEntity existing = userProfileRepo.findById(userId).orElseThrow();
            if (existing.getStatus() != UserStatus.INACTIVE) {
                log.info("Onboarding already completed for user {}", userId);
                return getStatus(userId);
            }
            // INACTIVE user re-onboarding — allow them to create a new workspace
            isReactivation = true;
        }

        if (request.getUserType() == null) {
            throw new SocialRavenException("userType is required", HttpStatus.BAD_REQUEST);
        }

        UserType userType = request.getUserType();

        if (userType == UserType.AGENCY &&
                (request.getWorkspaceNames() == null || request.getWorkspaceNames().isEmpty()
                        || request.getWorkspaceNames().get(0).isBlank())) {
            throw new SocialRavenException("At least one workspace name is required for AGENCY accounts", HttpStatus.BAD_REQUEST);
        }

        if (userType == UserType.AGENCY && request.getWorkspaceNames().size() > 10) {
            throw new SocialRavenException("You can create at most 10 workspaces during onboarding", HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 1. Persist or reactivate user profile
        if (!isReactivation) {
            UserProfileEntity profile = new UserProfileEntity();
            profile.setUserId(userId);
            profile.setUserType(userType);
            profile.setStatus(UserStatus.ACTIVE);
            profile.setCanCreateWorkspaces(true);
            profile.setCreatedAt(now);
            profile.setUpdatedAt(now);
            userProfileRepo.save(profile);
        } else {
            UserProfileEntity profile = userProfileRepo.findById(userId).orElseThrow();
            profile.setUserType(userType);
            profile.setStatus(UserStatus.ACTIVE);
            profile.setCanCreateWorkspaces(true);
            profile.setUpdatedAt(now);
            userProfileRepo.save(profile);
            log.info("User reactivated via re-onboarding: userId={}", userId);
        }
        requestAccessCacheService.cacheUserStatus(userId, UserStatus.ACTIVE);

        // 2. Determine workspace names to create
        List<String> namesToCreate;
        String firstWorkspaceId;

        if (userType == UserType.INFLUENCER) {
            // Influencer gets a single personal workspace.
            // On initial onboarding use a stable "personal_<userId>" ID;
            // on re-activation use a new UUID to avoid conflicts with the old workspace.
            namesToCreate = List.of("main");
            firstWorkspaceId = isReactivation ? UUID.randomUUID().toString() : "personal_" + userId;
        } else {
            namesToCreate = request.getWorkspaceNames().stream()
                    .map(String::trim)
                    .filter(n -> !n.isBlank())
                    .toList();
            firstWorkspaceId = null; // will be set below
        }

        String activeWorkspaceId = null;

        for (int i = 0; i < namesToCreate.size(); i++) {
            String workspaceId = (userType == UserType.INFLUENCER)
                    ? firstWorkspaceId
                    : UUID.randomUUID().toString();
            String workspaceName = namesToCreate.get(i);

            WorkspaceEntity workspace = new WorkspaceEntity();
            workspace.setId(workspaceId);
            workspace.setName(workspaceName);
            workspace.setCompanyName(i == 0 && request.getCompanyName() != null
                    ? request.getCompanyName().trim() : null);
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
            requestAccessCacheService.cacheWorkspaceRole(workspaceId, userId, WorkspaceRole.OWNER);

            WorkspaceSettingsEntity settings = new WorkspaceSettingsEntity();
            settings.setWorkspaceId(workspaceId);
            settings.setDefaultTz("UTC");
            settings.setUpdatedAt(now);
            workspaceSettingsRepo.save(settings);

            if (i == 0) activeWorkspaceId = workspaceId;
        }

        log.info("Onboarding completed for user {} as {} with {} workspace(s), active={}",
                userId, userType, namesToCreate.size(), activeWorkspaceId);
        return new OnboardingStatusResponse(true, userType.name(), activeWorkspaceId, true);
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
