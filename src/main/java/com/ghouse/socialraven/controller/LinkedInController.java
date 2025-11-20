package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.LinkedInPostDto;
import com.ghouse.socialraven.service.LinkedInService;
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
    private LinkedInService linkedInService;

    @PostMapping
    @RequestMapping("/post")
    public String postMessage(@RequestBody LinkedInPostDto linkedInPost) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        linkedInService.postToLinkedIn(userId, linkedInPost.getMessage());
        return "SUCCESS";
    }
}
