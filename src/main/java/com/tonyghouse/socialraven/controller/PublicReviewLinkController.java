package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.dto.PostCollaborationThreadResponse;
import com.tonyghouse.socialraven.dto.PublicPostCollectionReviewResponse;
import com.tonyghouse.socialraven.dto.PublicReviewCommentRequest;
import com.tonyghouse.socialraven.dto.PublicReviewDecisionRequest;
import com.tonyghouse.socialraven.service.post.PostCollectionReviewLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/review-links")
public class PublicReviewLinkController {

    @Autowired
    private PostCollectionReviewLinkService postCollectionReviewLinkService;

    @GetMapping("/{token}")
    public PublicPostCollectionReviewResponse getPublicReview(@PathVariable String token) {
        return postCollectionReviewLinkService.getPublicReview(token);
    }

    @PostMapping("/{token}/comments")
    public PostCollaborationThreadResponse addClientComment(@PathVariable String token,
                                                            @RequestBody PublicReviewCommentRequest request) {
        return postCollectionReviewLinkService.addClientComment(token, request);
    }

    @PostMapping("/{token}/approve")
    public PublicPostCollectionReviewResponse approveFromClient(@PathVariable String token,
                                                                @RequestBody(required = false) PublicReviewDecisionRequest request) {
        return postCollectionReviewLinkService.approveFromClient(token, request);
    }

    @PostMapping("/{token}/reject")
    public PublicPostCollectionReviewResponse rejectFromClient(@PathVariable String token,
                                                               @RequestBody(required = false) PublicReviewDecisionRequest request) {
        return postCollectionReviewLinkService.rejectFromClient(token, request);
    }
}
