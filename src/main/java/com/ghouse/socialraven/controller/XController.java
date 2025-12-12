package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.XPost;
import com.ghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
