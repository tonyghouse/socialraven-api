package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.dto.CalendarPostResponse;
import com.ghouse.socialraven.dto.PostResponse;
import com.ghouse.socialraven.dto.PostCollection;
import com.ghouse.socialraven.service.post.PostService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/posts")
public class PostController {

    @Autowired
    private PostService postService;


    @GetMapping("/")
    public Page<PostResponse> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "POSTED") PostStatus postStatus
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.getUserPosts(userId, page, postStatus);
    }

    //TODO-REMOVE
    @GetMapping("/all")
    public Page<PostResponse> getAllPosts() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.getUserPosts(userId);
    }


    @GetMapping("/{postId}")
    public PostResponse getPostById(@PathVariable Long postId
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        PostResponse post = postService.getPostById(userId, postId);
        return post;
    }

    @DeleteMapping("/{postId}")
    public void deletePostById(@PathVariable Long postId
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        postService.deletePostById(userId, postId);
    }

    @GetMapping("/calendar")
    public List<CalendarPostResponse> getCalendarPosts(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) List<String> providerUserIds) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        OffsetDateTime start = OffsetDateTime.parse(startDate);
        OffsetDateTime end = OffsetDateTime.parse(endDate);
        return postService.getCalendarPosts(userId, start, end, providerUserIds);
    }

}
