package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.annotation.RequiresRole;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.workspace.UpsertWorkspaceLibraryBundleRequest;
import com.tonyghouse.socialraven.dto.workspace.UpsertWorkspaceLibraryItemRequest;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceLibraryBundleResponse;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceLibraryItemResponse;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceLibraryResponse;
import com.tonyghouse.socialraven.service.workspace.WorkspaceLibraryService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspace-library")
public class WorkspaceLibraryController {

    @Autowired
    private WorkspaceLibraryService workspaceLibraryService;

    @GetMapping
    public WorkspaceLibraryResponse getWorkspaceLibrary(
            @RequestParam(defaultValue = "false") boolean approvedOnly) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return workspaceLibraryService.getWorkspaceLibrary(userId, approvedOnly);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/items")
    public WorkspaceLibraryItemResponse createLibraryItem(
            @RequestBody UpsertWorkspaceLibraryItemRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return workspaceLibraryService.createLibraryItem(userId, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PatchMapping("/items/{itemId}")
    public WorkspaceLibraryItemResponse updateLibraryItem(
            @PathVariable Long itemId,
            @RequestBody UpsertWorkspaceLibraryItemRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return workspaceLibraryService.updateLibraryItem(userId, itemId, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteLibraryItem(@PathVariable Long itemId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        workspaceLibraryService.deleteLibraryItem(userId, itemId);
        return ResponseEntity.noContent().build();
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/bundles")
    public WorkspaceLibraryBundleResponse createLibraryBundle(
            @RequestBody UpsertWorkspaceLibraryBundleRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return workspaceLibraryService.createLibraryBundle(userId, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PatchMapping("/bundles/{bundleId}")
    public WorkspaceLibraryBundleResponse updateLibraryBundle(
            @PathVariable Long bundleId,
            @RequestBody UpsertWorkspaceLibraryBundleRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return workspaceLibraryService.updateLibraryBundle(userId, bundleId, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @DeleteMapping("/bundles/{bundleId}")
    public ResponseEntity<Void> deleteLibraryBundle(@PathVariable Long bundleId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        workspaceLibraryService.deleteLibraryBundle(userId, bundleId);
        return ResponseEntity.noContent().build();
    }
}
