package com.tonyghouse.socialraven.service.workspace;

import com.tonyghouse.socialraven.constant.UserStatus;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.workspace.MemberResponse;
import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.UserProfileRepo;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
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

        target.setRole(newRole);
        memberRepo.save(target);
        log.info("Member role updated: workspaceId={}, userId={}, newRole={}", workspaceId, targetUserId, newRole);
        ClerkUserService.UserProfile profile = clerkUserService.getUserProfile(target.getUserId());
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

        memberRepo.delete(target);
        log.info("Member removed: workspaceId={}, userId={}, by={}", workspaceId, targetUserId, callerUserId);

        // Deactivate user if they no longer belong to any workspace
        List<WorkspaceMemberEntity> remaining = memberRepo.findAllByUserId(targetUserId);
        if (remaining.isEmpty()) {
            userProfileRepo.findById(targetUserId).ifPresent(profile -> {
                profile.setStatus(UserStatus.INACTIVE);
                profile.setUpdatedAt(OffsetDateTime.now());
                userProfileRepo.save(profile);
                log.info("User deactivated (no remaining workspaces): userId={}", targetUserId);
            });
        }
    }
}
