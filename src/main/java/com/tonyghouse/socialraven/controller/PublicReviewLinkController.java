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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/review-links")
public class PublicReviewLinkController {

    private static final String REVIEW_LINK_PASSCODE_HEADER = "X-Review-Link-Passcode";

    @Autowired
    private PostCollectionReviewLinkService postCollectionReviewLinkService;

    @GetMapping("/{token}")
    public PublicPostCollectionReviewResponse getPublicReview(
            @PathVariable String token,
            @RequestHeader(value = REVIEW_LINK_PASSCODE_HEADER, required = false) String passcode
    ) {
        return postCollectionReviewLinkService.getPublicReview(token, passcode);
    }

    @PostMapping("/{token}/comments")
    public PostCollaborationThreadResponse addClientComment(
            @PathVariable String token,
            @RequestHeader(value = REVIEW_LINK_PASSCODE_HEADER, required = false) String passcode,
            @RequestBody PublicReviewCommentRequest request
    ) {
        return postCollectionReviewLinkService.addClientComment(token, passcode, request);
    }

    @PostMapping("/{token}/approve")
    public PublicPostCollectionReviewResponse approveFromClient(
            @PathVariable String token,
            @RequestHeader(value = REVIEW_LINK_PASSCODE_HEADER, required = false) String passcode,
            @RequestBody(required = false) PublicReviewDecisionRequest request
    ) {
        return postCollectionReviewLinkService.approveFromClient(token, passcode, request);
    }

    @PostMapping("/{token}/reject")
    public PublicPostCollectionReviewResponse rejectFromClient(
            @PathVariable String token,
            @RequestHeader(value = REVIEW_LINK_PASSCODE_HEADER, required = false) String passcode,
            @RequestBody(required = false) PublicReviewDecisionRequest request
    ) {
        return postCollectionReviewLinkService.rejectFromClient(token, passcode, request);
    }
}
