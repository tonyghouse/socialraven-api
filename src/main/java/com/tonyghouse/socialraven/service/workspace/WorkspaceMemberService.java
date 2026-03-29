package com.tonyghouse.socialraven.service.workspace;

import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.workspace.MemberResponse;
import com.tonyghouse.socialraven.entity.UserProfileEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.UserProfileRepo;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WorkspaceMemberService {

    @Autowired
    private WorkspaceMemberRepo memberRepo;

    @Autowired
    private UserProfileRepo userProfileRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ClerkUserService clerkUserService;

    /**
     * Returns all members of the workspace, enriched with Clerk profile data.
     * Caller must be ADMIN+ (enforced in controller).
     */
    public List<MemberResponse> getMembers(String workspaceId) {
        return memberRepo.findAllByWorkspaceId(workspaceId)
                .stream()
                .map(m -> {
                    ClerkUserService.UserProfile profile = clerkUserService.getUserProfile(m.getUserId());
                    return new MemberResponse(
                            m.getUserId(),
                            m.getRole(),
                            m.getJoinedAt(),
                            profile != null ? profile.firstName() : null,
                            profile != null ? profile.lastName() : null,
                            profile != null ? profile.email() : null
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Changes the role of a member.
     * Only OWNER can change roles. OWNER role cannot be reassigned this way.
     */
    @Transactional
    public MemberResponse updateMemberRole(String workspaceId,
                                           String targetUserId,
                                           String callerUserId,
                                           WorkspaceRole newRole) {
        // Verify caller is OWNER
        WorkspaceRole callerRole = memberRepo.findByWorkspaceIdAndUserId(workspaceId, callerUserId)
                .map(WorkspaceMemberEntity::getRole)
                .orElseThrow(() -> new SocialRavenException("Access denied", HttpStatus.FORBIDDEN));

        if (callerRole != WorkspaceRole.OWNER) {
            throw new SocialRavenException("Only the workspace OWNER can change member roles", HttpStatus.FORBIDDEN);
        }

        // Cannot assign OWNER role via this endpoint (ownership transfer is separate)
        if (newRole == WorkspaceRole.OWNER) {
            throw new SocialRavenException("Cannot assign OWNER role this way", HttpStatus.BAD_REQUEST);
        }

        // Cannot demote yourself
        if (targetUserId.equals(callerUserId)) {
            throw new SocialRavenException("Cannot change your own role", HttpStatus.BAD_REQUEST);
        }

        WorkspaceMemberEntity target = memberRepo.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new SocialRavenException("Member not found", HttpStatus.NOT_FOUND));

        if (target.getRole() == WorkspaceRole.OWNER) {
            throw new SocialRavenException("Cannot change the OWNER's role", HttpStatus.BAD_REQUEST);
        }

        WorkspaceRole previousRole = target.getRole();
        boolean roleChanged = previousRole != newRole;

        target.setRole(newRole);
        memberRepo.save(target);
        if (newRole == WorkspaceRole.ADMIN) {
            userProfileRepo.findById(targetUserId).ifPresentOrElse(profile -> {
                if (!profile.isCanCreateWorkspaces()) {
                    profile.setCanCreateWorkspaces(true);
                    profile.setUpdatedAt(OffsetDateTime.now());
                    userProfileRepo.save(profile);
                }
            }, () -> {
                UserProfileEntity profile = new UserProfileEntity();
                profile.setUserId(targetUserId);
                profile.setUserType(com.tonyghouse.socialraven.constant.UserType.INFLUENCER);
                profile.setStatus(com.tonyghouse.socialraven.constant.UserStatus.ACTIVE);
                profile.setCanCreateWorkspaces(true);
                profile.setCreatedAt(OffsetDateTime.now());
                profile.setUpdatedAt(OffsetDateTime.now());
                userProfileRepo.save(profile);
            });
        }
        log.info("Member role updated: workspaceId={}, userId={}, newRole={}", workspaceId, targetUserId, newRole);

        ClerkUserService.UserProfile profile = clerkUserService.getUserProfile(target.getUserId());
        if (roleChanged && profile != null && profile.email() != null && !profile.email().isBlank()) {
            try {
                emailService.sendRoleChangedEmail(
                        profile.email().trim(),
                        getWorkspaceName(workspaceId),
                        resolveDisplayName(callerUserId),
                        previousRole,
                        newRole
                );
            } catch (Exception e) {
                log.warn("Role updated but notification email failed for userId={}: {}", targetUserId, e.getMessage());
            }
        }

        return new MemberResponse(target.getUserId(), target.getRole(), target.getJoinedAt(),
                profile != null ? profile.firstName() : null,
                profile != null ? profile.lastName() : null,
                profile != null ? profile.email() : null);
    }

    /**
     * Removes a member from the workspace.
     * ADMIN+ can remove MEMBER/VIEWER. OWNER can remove anyone (except themselves).
     */
    @Transactional
    public void removeMember(String workspaceId, String targetUserId, String callerUserId) {
        WorkspaceRole callerRole = memberRepo.findByWorkspaceIdAndUserId(workspaceId, callerUserId)
                .map(WorkspaceMemberEntity::getRole)
                .orElseThrow(() -> new SocialRavenException("Access denied", HttpStatus.FORBIDDEN));

        if (callerRole == WorkspaceRole.MEMBER || callerRole == WorkspaceRole.VIEWER) {
            throw new SocialRavenException("ADMIN or OWNER role required to remove members", HttpStatus.FORBIDDEN);
        }

        WorkspaceMemberEntity target = memberRepo.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new SocialRavenException("Member not found", HttpStatus.NOT_FOUND));

        if (target.getRole() == WorkspaceRole.OWNER) {
            throw new SocialRavenException("Cannot remove the workspace OWNER", HttpStatus.BAD_REQUEST);
        }

        // ADMIN cannot remove another ADMIN (only OWNER can)
        if (callerRole == WorkspaceRole.ADMIN && target.getRole() == WorkspaceRole.ADMIN) {
            throw new SocialRavenException("Only the OWNER can remove another ADMIN", HttpStatus.FORBIDDEN);
        }

        ClerkUserService.UserProfile targetProfile = clerkUserService.getUserProfile(targetUserId);
        memberRepo.delete(target);
        log.info("Member removed: workspaceId={}, userId={}, by={}", workspaceId, targetUserId, callerUserId);

        if (targetProfile != null && targetProfile.email() != null && !targetProfile.email().isBlank()) {
            try {
                emailService.sendMemberRemovedEmail(
                        targetProfile.email().trim(),
                        getWorkspaceName(workspaceId),
                        resolveDisplayName(callerUserId)
                );
            } catch (Exception e) {
                log.warn("Member removed but notification email failed for userId={}: {}", targetUserId, e.getMessage());
            }
        }
    }

    private String getWorkspaceName(String workspaceId) {
        WorkspaceEntity workspace = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));
        return workspace.getName();
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
}
