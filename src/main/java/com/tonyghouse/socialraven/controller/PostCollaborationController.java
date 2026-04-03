package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.annotation.RequiresRole;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.PostCollaborationReplyRequest;
import com.tonyghouse.socialraven.dto.PostCollaborationThreadRequest;
import com.tonyghouse.socialraven.dto.PostCollaborationThreadResponse;
import com.tonyghouse.socialraven.service.post.PostCollaborationService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/post-collections/{collectionId}/collaboration")
public class PostCollaborationController {

    @Autowired
    private PostCollaborationService postCollaborationService;

    @GetMapping("/threads")
    public List<PostCollaborationThreadResponse> getThreads(@PathVariable Long collectionId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postCollaborationService.getThreads(userId, collectionId);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/threads")
    public PostCollaborationThreadResponse createThread(@PathVariable Long collectionId,
                                                        @RequestBody PostCollaborationThreadRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postCollaborationService.createThread(userId, collectionId, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/threads/{threadId}/replies")
    public PostCollaborationThreadResponse addReply(@PathVariable Long collectionId,
                                                    @PathVariable Long threadId,
                                                    @RequestBody PostCollaborationReplyRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postCollaborationService.addReply(userId, collectionId, threadId, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/threads/{threadId}/resolve")
    public PostCollaborationThreadResponse resolveThread(@PathVariable Long collectionId,
                                                         @PathVariable Long threadId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postCollaborationService.resolveThread(userId, collectionId, threadId);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/threads/{threadId}/reopen")
    public PostCollaborationThreadResponse reopenThread(@PathVariable Long collectionId,
                                                        @PathVariable Long threadId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postCollaborationService.reopenThread(userId, collectionId, threadId);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/threads/{threadId}/accept-suggestion")
    public PostCollaborationThreadResponse acceptSuggestion(@PathVariable Long collectionId,
                                                            @PathVariable Long threadId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postCollaborationService.acceptSuggestion(userId, collectionId, threadId);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/threads/{threadId}/reject-suggestion")
    public PostCollaborationThreadResponse rejectSuggestion(@PathVariable Long collectionId,
                                                            @PathVariable Long threadId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postCollaborationService.rejectSuggestion(userId, collectionId, threadId);
    }
}
