package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.dto.workspace.AcceptInviteRequest;
import com.tonyghouse.socialraven.dto.workspace.InvitationResponse;
import com.tonyghouse.socialraven.dto.workspace.InviteRequest;
import com.tonyghouse.socialraven.service.workspace.WorkspaceInvitationService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Invitation endpoints.
 *
 * POST  /workspaces/invite                   — send invite (multi-workspace, Option A)
 * GET   /workspaces/{id}/invitations         — list pending invitations for a workspace
 * DELETE /workspaces/invitations/{token}     — revoke an invitation
 * POST  /workspaces/invitations/accept       — accept an invitation by token
 *
 * Note: these endpoints are excluded from WorkspaceAccessFilter (no X-Workspace-Id required)
 * because the invite and accept flows operate at the user level, not within a single workspace.
 */
@RestController
@RequestMapping("/workspaces")
public class WorkspaceInvitationController {

    @Autowired
    private WorkspaceInvitationService invitationService;

    /**
     * POST /workspaces/invite
     * Sends invitation email to one person for one or more workspaces (Option A).
     * Caller must be ADMIN+ in each selected workspace.
     */
    @PostMapping("/invite")
    public List<InvitationResponse> invite(@RequestBody InviteRequest request) {
        String callerId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return invitationService.invite(callerId, request);
    }

    /**
     * GET /workspaces/{id}/invitations
     * Lists pending invitations for the workspace.
     * Caller must be a member (filter) and ADMIN+ (service).
     */
    @GetMapping("/{id}/invitations")
    public List<InvitationResponse> getInvitations(@PathVariable String id) {
        return invitationService.getInvitations(id);
    }

    /**
     * DELETE /workspaces/invitations/{token}
     * Revokes a pending invitation.
     */
    @DeleteMapping("/invitations/{token}")
    public ResponseEntity<Void> revokeInvitation(@PathVariable UUID token) {
        String callerId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        invitationService.revokeInvitation(token, callerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /workspaces/invitations/accept
     * Accepts an invitation by token. Caller must be authenticated (Clerk JWT).
     * Does NOT require X-Workspace-Id header.
     */
    @PostMapping("/invitations/accept")
    public ResponseEntity<Map<String, String>> acceptInvitation(@RequestBody AcceptInviteRequest request) {
        String callerId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        String workspaceId = invitationService.acceptInvitation(request, callerId);
        return ResponseEntity.ok(Map.of("workspaceId", workspaceId));
    }
}
