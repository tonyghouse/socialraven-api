package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.dto.XPost;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.JedisPool;

@RestController
public class XController {

//    @Autowired
//    private XService xService;

    @Autowired
    private JedisPool jedisPool;

    @PostMapping
    @RequestMapping("/x/tweet")
    public String postTweet(@RequestBody XPost xPost) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
//        xService.postTweet(userId, xPost.getMessage());
        return "SUCCESS";
    }


}
