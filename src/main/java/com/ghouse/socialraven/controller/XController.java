package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.LinkedInPostDto;
import com.ghouse.socialraven.dto.XPostDto;
import com.ghouse.socialraven.service.XService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/x")
public class XController {

    @Autowired
    private XService xService;

    @PostMapping
    @RequestMapping("/tweet")
    public String postTweet(@RequestBody XPostDto xPost) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        xService.postTweet(userId, xPost.getMessage());
        return "SUCCESS";
    }
}
