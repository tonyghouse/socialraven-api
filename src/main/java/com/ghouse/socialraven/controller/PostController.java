package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.dto.PostResponse;
import com.ghouse.socialraven.dto.SchedulePost;
import com.ghouse.socialraven.service.post.PostService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts")
public class PostController {

    @Autowired
    private PostService postService;

    @PostMapping
    @RequestMapping("/schedule")
    public SchedulePost schedulePost(@RequestBody SchedulePost schedulePost) {
        return postService.schedulePost(schedulePost);
    }

    @GetMapping("/")
    public Page<PostResponse> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "POSTED") PostStatus postStatus
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        System.out.println("");
        return postService.getUserPosts(userId, page, postStatus);
    }

    @GetMapping("/all")
    public Page<PostResponse> getAllPosts() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.getUserPosts(userId);
    }




}
