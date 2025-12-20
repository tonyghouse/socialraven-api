package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.util.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;

@RestController
@RequestMapping("/oauth/x")
@RequiredArgsConstructor
@Slf4j
public class XOAuthTokenStorageController {

    private final JedisPool jedisPool;

    /**
     * Store token secret temporarily during OAuth flow
     * Called from Next.js /api/auth/x/route.ts
     */
    @PostMapping("/store-token-secret")
    public ResponseEntity<Map<String, String>> storeTokenSecret(
            @RequestBody Map<String, String> request
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        String oauthToken = request.get("oauthToken");
        String oauthTokenSecret = request.get("oauthTokenSecret");

        if (oauthToken == null || oauthTokenSecret == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing parameters"));
        }

        String redisKey = "x_oauth_token_secret:" + userId + ":" + oauthToken;

        // ✅ Jedis: SET with TTL (10 minutes = 600 seconds)
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(redisKey, 600, oauthTokenSecret);
        }

        log.info(
                "Stored token secret for user: {} with token: {}",
                userId,
                oauthToken.substring(0, Math.min(10, oauthToken.length())) + "..."
        );

        return ResponseEntity.ok(Map.of("status", "success"));
    }

    /**
     * Retrieve token secret during callback
     * Called from Next.js /api/auth/x/callback/route.ts
     */
    @GetMapping("/get-token-secret")
    public ResponseEntity<Map<String, String>> getTokenSecret(
            @RequestParam String oauthToken
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        String redisKey = "x_oauth_token_secret:" + userId + ":" + oauthToken;

        String tokenSecret;

        try (Jedis jedis = jedisPool.getResource()) {
            tokenSecret = jedis.get(redisKey);

            if (tokenSecret == null) {
                log.warn(
                        "Token secret not found for user: {} with token: {}",
                        userId,
                        oauthToken
                );
                return ResponseEntity.notFound().build();
            }

            // ✅ One-time use → delete after read
            jedis.del(redisKey);
        }

        log.info("Retrieved and deleted token secret for user: {}", userId);

        return ResponseEntity.ok(Map.of("oauthTokenSecret", tokenSecret));
    }
}
