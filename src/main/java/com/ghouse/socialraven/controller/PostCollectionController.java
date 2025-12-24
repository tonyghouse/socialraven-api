package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.PostCollection;
import com.ghouse.socialraven.service.post.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

}