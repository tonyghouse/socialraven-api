package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.annotation.RequiresRole;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.PostCollection;
import com.tonyghouse.socialraven.dto.PostCollectionReviewActionRequest;
import com.tonyghouse.socialraven.dto.PostCollectionResponse;
import com.tonyghouse.socialraven.dto.ScheduleDraftRequest;
import com.tonyghouse.socialraven.dto.UpdatePostCollectionRequest;
import com.tonyghouse.socialraven.service.post.PostService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import java.util.List;

@RestController
@RequestMapping("/post-collections")
public class PostCollectionController {

    @Autowired
    private PostService postService;

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/schedule")
    public PostCollectionResponse schedulePost(@RequestBody PostCollection postCollection) {
        return postService.schedulePostCollection(postCollection);
    }

    @GetMapping
    public Page<PostCollectionResponse> getPostCollections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> providerUserIds,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) String dateRange) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.getUserPostCollections(userId, page, type, search, providerUserIds, platform, sortDir, dateRange);
    }

    @GetMapping("/{id}")
    public PostCollectionResponse getPostCollectionById(@PathVariable Long id) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.getPostCollectionById(userId, id);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePostCollection(@PathVariable Long id) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        postService.deletePostCollection(userId, id);
        return ResponseEntity.noContent().build();
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PatchMapping("/{id}")
    public PostCollectionResponse updatePostCollection(
            @PathVariable Long id,
            @RequestBody UpdatePostCollectionRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.updatePostCollection(userId, id, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/{id}/schedule")
    public PostCollectionResponse scheduleDraft(
            @PathVariable Long id,
            @RequestBody ScheduleDraftRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.scheduleDraftCollection(userId, id, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/{id}/activate-approved-schedule")
    public PostCollectionResponse activateApprovedSchedule(@PathVariable Long id) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.activateApprovedSchedule(userId, id);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/{id}/recovery-draft")
    public PostCollectionResponse createRecoveryDraft(@PathVariable Long id) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.createRecoveryDraft(userId, id);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/{id}/approve")
    public PostCollectionResponse approvePostCollection(
            @PathVariable Long id,
            @RequestBody(required = false) PostCollectionReviewActionRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.approvePostCollection(userId, id, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/{id}/request-changes")
    public PostCollectionResponse requestChanges(
            @PathVariable Long id,
            @RequestBody(required = false) PostCollectionReviewActionRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.requestChanges(userId, id, request);
    }

    @GetMapping("/{id}/approval-log/export")
    public ResponseEntity<byte[]> exportApprovalLog(@PathVariable Long id) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        byte[] csv = postService.exportApprovalLog(userId, id);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"post-collection-" + id + "-approval-log.csv\""
                )
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

}
