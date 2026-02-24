package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.PostCollection;
import com.ghouse.socialraven.dto.PostCollectionResponse;
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
@RequestMapping("/post-collections")
public class PostCollectionController {

    @Autowired
    private PostService postService;

    @PostMapping
    @RequestMapping("/schedule")
    public PostCollection schedulePost(@RequestBody PostCollection postCollection) {
        return postService.schedulePostCollection(postCollection);
    }

    @GetMapping
    public Page<PostCollectionResponse> getPostCollections(
            @RequestParam(defaultValue = "0") int page) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return postService.getUserPostCollections(userId, page);
    }

}
