package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.dto.workspace.CreateWorkspaceRequest;
import com.tonyghouse.socialraven.dto.workspace.UpdateWorkspaceRequest;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceResponse;
import com.tonyghouse.socialraven.service.workspace.WorkspaceService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

    @Autowired
    private WorkspaceService workspaceService;

    /** Returns all workspaces the caller belongs to. */
    @GetMapping("/mine")
    public List<WorkspaceResponse> getMyWorkspaces() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return workspaceService.getMyWorkspaces(userId);
    }

    @GetMapping("/deleted")
    public List<WorkspaceResponse> getDeletedWorkspaces() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return workspaceService.getDeletedWorkspaces(userId);
    }

    /** Creates a new workspace (plan-gated by max_workspaces). */
    @PostMapping
    public WorkspaceResponse createWorkspace(@RequestBody CreateWorkspaceRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return workspaceService.createWorkspace(userId, request);
    }

    /** Returns details for the workspace identified by the X-Workspace-Id header. */
    @GetMapping("/{id}")
    public WorkspaceResponse getWorkspace(@PathVariable String id) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return workspaceService.getWorkspace(id, userId);
    }

    /** Updates workspace name, company name, or logo. Requires ADMIN+ role. */
    @PatchMapping("/{id}")
    public WorkspaceResponse updateWorkspace(
            @PathVariable String id,
            @RequestBody UpdateWorkspaceRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return workspaceService.updateWorkspace(id, userId, request);
    }

    /** Deletes a workspace. Requires ADMIN or OWNER role. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable String id) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        workspaceService.deleteWorkspace(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public WorkspaceResponse restoreWorkspace(@PathVariable String id) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return workspaceService.restoreWorkspace(id, userId);
    }
}
