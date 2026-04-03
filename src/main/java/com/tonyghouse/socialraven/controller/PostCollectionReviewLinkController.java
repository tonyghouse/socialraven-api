package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.annotation.RequiresRole;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.CreatePostCollectionReviewLinkRequest;
import com.tonyghouse.socialraven.dto.PostCollectionReviewLinkResponse;
import com.tonyghouse.socialraven.service.post.PostCollectionReviewLinkService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/post-collections/{collectionId}/review-links")
public class PostCollectionReviewLinkController {

    @Autowired
    private PostCollectionReviewLinkService postCollectionReviewLinkService;

    @RequiresRole(WorkspaceRole.EDITOR)
    @GetMapping
    public List<PostCollectionReviewLinkResponse> getReviewLinks(@PathVariable Long collectionId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postCollectionReviewLinkService.getReviewLinks(userId, collectionId);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping
    public PostCollectionReviewLinkResponse createReviewLink(@PathVariable Long collectionId,
                                                             @RequestBody(required = false) CreatePostCollectionReviewLinkRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postCollectionReviewLinkService.createReviewLink(userId, collectionId, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @DeleteMapping("/{reviewLinkId}")
    public ResponseEntity<Void> revokeReviewLink(@PathVariable Long collectionId,
                                                 @PathVariable String reviewLinkId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        postCollectionReviewLinkService.revokeReviewLink(userId, collectionId, reviewLinkId);
        return ResponseEntity.noContent().build();
    }
}
