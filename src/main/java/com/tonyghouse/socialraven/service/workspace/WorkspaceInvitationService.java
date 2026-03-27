package com.tonyghouse.socialraven.service.workspace;

import com.tonyghouse.socialraven.constant.UserStatus;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.workspace.AcceptInviteRequest;
import com.tonyghouse.socialraven.dto.workspace.InvitationResponse;
import com.tonyghouse.socialraven.dto.workspace.InviteRequest;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.entity.WorkspaceInvitationEntity;
import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.UserProfileRepo;
import com.tonyghouse.socialraven.repo.WorkspaceInvitationRepo;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WorkspaceInvitationService {

    @Autowired
    private WorkspaceInvitationRepo invitationRepo;

    @Autowired
    private WorkspaceMemberRepo memberRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private UserProfileRepo userProfileRepo;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ClerkUserService clerkUserService;

    /**
     * Option A: invite by email to one or more workspaces at once.
     * Creates one workspace_invitation row per workspace, sends one email.
     * Caller must be ADMIN or OWNER in every selected workspace.
     *
     * Returns the list of created invitation tokens (one per workspace).
     */
    @Transactional
    public List<InvitationResponse> invite(String callerUserId, InviteRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            throw new SocialRavenException("Invited email is required", HttpStatus.BAD_REQUEST);
        }
        if (req.getRole() == null) {
            throw new SocialRavenException("Role is required", HttpStatus.BAD_REQUEST);
        }
        if (req.getWorkspaceIds() == null || req.getWorkspaceIds().isEmpty()) {
            throw new SocialRavenException("At least one workspace must be selected", HttpStatus.BAD_REQUEST);
        }
        // Cannot invite as OWNER
        if (req.getRole() == WorkspaceRole.OWNER) {
            throw new SocialRavenException("Cannot invite someone as OWNER", HttpStatus.BAD_REQUEST);
        }

        String invitedEmail = req.getEmail().trim().toLowerCase();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusDays(7);

        List<String> workspaceNames = new ArrayList<>();
        List<WorkspaceInvitationEntity> created = new ArrayList<>();

        for (String workspaceId : req.getWorkspaceIds()) {
            WorkspaceEntity workspace = workspaceRepo.findById(workspaceId)
                    .orElseThrow(() -> new SocialRavenException("Workspace not found: " + workspaceId, HttpStatus.NOT_FOUND));

            // Verify caller is ADMIN+ in this workspace
            WorkspaceRole callerRole = memberRepo.findByWorkspaceIdAndUserId(workspaceId, callerUserId)
                    .map(m -> m.getRole())
                    .orElseThrow(() -> new SocialRavenException("You are not a member of workspace: " + workspaceId, HttpStatus.FORBIDDEN));

            if (callerRole == WorkspaceRole.MEMBER || callerRole == WorkspaceRole.VIEWER) {
                throw new SocialRavenException("ADMIN or OWNER role required to invite members", HttpStatus.FORBIDDEN);
            }

            // Check if the user is already a member (by email—we can't check by userId since we only have email)
            // We create the invitation; duplicate membership will be caught at accept time.

            WorkspaceInvitationEntity invitation = new WorkspaceInvitationEntity();
            invitation.setToken(UUID.randomUUID());
            invitation.setWorkspaceId(workspaceId);
            invitation.setInvitedEmail(invitedEmail);
            invitation.setRole(req.getRole());
            invitation.setInvitedBy(callerUserId);
            invitation.setExpiresAt(expiresAt);
            invitation.setCreatedAt(now);
            invitationRepo.save(invitation);

            created.add(invitation);
            workspaceNames.add(workspace.getName());
        }

        // Resolve inviter's display name from Clerk
        String inviterName = callerUserId;
        ClerkUserService.UserProfile inviterProfile = clerkUserService.getUserProfile(callerUserId);
        if (inviterProfile != null) {
            String first = inviterProfile.firstName() != null ? inviterProfile.firstName().trim() : "";
            String last = inviterProfile.lastName() != null ? inviterProfile.lastName().trim() : "";
            String fullName = (first + " " + last).trim();
            if (!fullName.isEmpty()) {
                inviterName = fullName;
            } else if (inviterProfile.email() != null && !inviterProfile.email().isBlank()) {
                inviterName = inviterProfile.email();
            }
        }

        // Send one email listing all workspaces — use the first token as the invite link
        // (each token is per-workspace; the accept flow accepts ALL matching tokens for the email)
        UUID firstToken = created.get(0).getToken();
        try {
            emailService.sendInvitationEmail(invitedEmail, workspaceNames, inviterName, req.getRole(), firstToken);
        } catch (Exception e) {
            log.warn("Invitation created but email failed for {}: {}", invitedEmail, e.getMessage());
            // Don't roll back — the invitation exists; resend can be implemented later
        }

        return created.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Returns all pending (not accepted) invitations for a workspace.
     * Caller must be ADMIN+ (enforced in controller).
     */
    public List<InvitationResponse> getInvitations(String workspaceId) {
        return invitationRepo.findAllByWorkspaceIdAndAcceptedAtIsNull(workspaceId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Revokes a pending invitation.
     * Caller must be ADMIN+ in the invitation's workspace.
     */
    @Transactional
    public void revokeInvitation(UUID token, String callerUserId) {
        WorkspaceInvitationEntity invitation = invitationRepo.findByToken(token)
                .orElseThrow(() -> new SocialRavenException("Invitation not found", HttpStatus.NOT_FOUND));

        if (invitation.getAcceptedAt() != null) {
            throw new SocialRavenException("Cannot revoke an already accepted invitation", HttpStatus.BAD_REQUEST);
        }

        WorkspaceRole callerRole = memberRepo.findByWorkspaceIdAndUserId(invitation.getWorkspaceId(), callerUserId)
                .map(m -> m.getRole())
                .orElseThrow(() -> new SocialRavenException("Access denied", HttpStatus.FORBIDDEN));

        if (callerRole == WorkspaceRole.MEMBER || callerRole == WorkspaceRole.VIEWER) {
            throw new SocialRavenException("ADMIN or OWNER role required to revoke invitations", HttpStatus.FORBIDDEN);
        }

        invitationRepo.delete(invitation);
        log.info("Invitation revoked: token={}, by={}", token, callerUserId);
    }

    /**
     * Accept an invitation by token.
     * Validates: not expired, not already used, caller's email matches invited_email.
     * Then inserts a workspace_member row.
     *
     * @return the workspaceId that was joined (for redirect)
     */
    @Transactional
    public String acceptInvitation(AcceptInviteRequest req, String callerUserId) {
        if (req.getToken() == null) {
            throw new SocialRavenException("Token is required", HttpStatus.BAD_REQUEST);
        }

        WorkspaceInvitationEntity invitation = invitationRepo.findByToken(req.getToken())
                .orElseThrow(() -> new SocialRavenException("Invitation not found or already used", HttpStatus.NOT_FOUND));

        if (invitation.getAcceptedAt() != null) {
            throw new SocialRavenException("This invitation has already been accepted", HttpStatus.CONFLICT);
        }

        if (OffsetDateTime.now().isAfter(invitation.getExpiresAt())) {
            throw new SocialRavenException("This invitation has expired", HttpStatus.GONE);
        }

        // Verify the signed-in user's email matches the invited email (prevents token theft)
        String callerEmail = clerkUserService.getUserEmail(callerUserId);
        if (callerEmail == null || !callerEmail.trim().toLowerCase().equals(invitation.getInvitedEmail())) {
            throw new SocialRavenException(
                    "This invitation was sent to a different email address", HttpStatus.FORBIDDEN);
        }

        String workspaceId = invitation.getWorkspaceId();

        // Check if already a member — idempotent
        boolean alreadyMember = memberRepo.findByWorkspaceIdAndUserId(workspaceId, callerUserId).isPresent();
        if (!alreadyMember) {
            WorkspaceMemberEntity member = new WorkspaceMemberEntity();
            member.setWorkspaceId(workspaceId);
            member.setUserId(callerUserId);
            member.setRole(invitation.getRole());
            member.setJoinedAt(OffsetDateTime.now());
            memberRepo.save(member);
            log.info("Invitation accepted: userId={}, workspaceId={}", callerUserId, workspaceId);
        }

        // Mark invitation as used (single-use)
        invitation.setAcceptedAt(OffsetDateTime.now());
        invitationRepo.save(invitation);

        // Reactivate the user if they were previously deactivated
        userProfileRepo.findById(callerUserId).ifPresent(profile -> {
            if (profile.getStatus() == UserStatus.INACTIVE) {
                profile.setStatus(UserStatus.ACTIVE);
                profile.setUpdatedAt(OffsetDateTime.now());
                userProfileRepo.save(profile);
                log.info("User reactivated via invitation acceptance: userId={}", callerUserId);
            }
        });

        return workspaceId;
    }

    private InvitationResponse toResponse(WorkspaceInvitationEntity e) {
        return new InvitationResponse(
                e.getToken(),
                e.getWorkspaceId(),
                e.getInvitedEmail(),
                e.getRole(),
                e.getInvitedBy(),
                e.getExpiresAt(),
                e.getAcceptedAt(),
                e.getCreatedAt()
        );
    }
}
