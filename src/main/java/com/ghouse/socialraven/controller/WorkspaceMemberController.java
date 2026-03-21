package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.workspace.MemberResponse;
import com.ghouse.socialraven.dto.workspace.UpdateMemberRoleRequest;
import com.ghouse.socialraven.service.workspace.WorkspaceMemberService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Member management for a workspace.
 *
 * All endpoints require the caller to be a member of the workspace
 * (enforced by WorkspaceAccessFilter via X-Workspace-Id header).
 * Additional role checks are done in WorkspaceMemberService.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {

    @Autowired
    private WorkspaceMemberService memberService;

    /** GET /workspaces/{workspaceId}/members — list all members (ADMIN+) */
    @GetMapping
    public List<MemberResponse> getMembers(@PathVariable String workspaceId) {
        return memberService.getMembers(workspaceId);
    }

    /** PATCH /workspaces/{workspaceId}/members/{userId} — change a member's role (OWNER only) */
    @PatchMapping("/{userId}")
    public MemberResponse updateMemberRole(@PathVariable String workspaceId,
                                           @PathVariable String userId,
                                           @RequestBody UpdateMemberRoleRequest request) {
        String callerId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return memberService.updateMemberRole(workspaceId, userId, callerId, request.getRole());
    }

    /** DELETE /workspaces/{workspaceId}/members/{userId} — remove a member (ADMIN+) */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable String workspaceId,
                                              @PathVariable String userId) {
        String callerId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        memberService.removeMember(workspaceId, userId, callerId);
        return ResponseEntity.noContent().build();
    }
}
