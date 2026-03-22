package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.annotation.RequiresRole;
import com.ghouse.socialraven.constant.WorkspaceRole;
import com.ghouse.socialraven.dto.PostCollection;
import com.ghouse.socialraven.dto.PostCollectionResponse;
import com.ghouse.socialraven.dto.ScheduleDraftRequest;
import com.ghouse.socialraven.dto.UpdatePostCollectionRequest;
import com.ghouse.socialraven.service.post.PostService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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

    @RequiresRole(WorkspaceRole.MEMBER)
    @PostMapping("/schedule")
    public PostCollection schedulePost(@RequestBody PostCollection postCollection) {
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

    @RequiresRole(WorkspaceRole.MEMBER)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePostCollection(@PathVariable Long id) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        postService.deletePostCollection(userId, id);
        return ResponseEntity.noContent().build();
    }

    @RequiresRole(WorkspaceRole.MEMBER)
    @PatchMapping("/{id}")
    public PostCollectionResponse updatePostCollection(
            @PathVariable Long id,
            @RequestBody UpdatePostCollectionRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.updatePostCollection(userId, id, request);
    }

    @RequiresRole(WorkspaceRole.MEMBER)
    @PostMapping("/{id}/schedule")
    public PostCollectionResponse scheduleDraft(
            @PathVariable Long id,
            @RequestBody ScheduleDraftRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.scheduleDraftCollection(userId, id, request);
    }

}
