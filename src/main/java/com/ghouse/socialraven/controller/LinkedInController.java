package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.LinkedInPost;
import com.ghouse.socialraven.service.provider.LinkedInOAuthService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/linkedin")
public class LinkedInController {

    @Autowired
    private LinkedInOAuthService linkedInOAuthService;

    @PostMapping
    @RequestMapping("/post")
    public String postMessage(@RequestBody LinkedInPost linkedInPost) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
//        linkedInOAuthService.postToLinkedIn(userId, linkedInPost.getMessage());
        return "SUCCESS";
    }
}
