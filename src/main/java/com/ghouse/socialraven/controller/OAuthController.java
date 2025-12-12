package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.dto.XOAuthCallbackRequest;
import com.ghouse.socialraven.service.provider.FacebookOAuthService;
import com.ghouse.socialraven.service.provider.InstagramOAuthService;
import com.ghouse.socialraven.service.provider.LinkedInOAuthService;
import com.ghouse.socialraven.service.provider.XOAuthService;
import com.ghouse.socialraven.service.provider.YouTubeOAuthService;
import com.ghouse.socialraven.service.session.OAuthSessionService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import lombok.extern.slf4j.Slf4j;
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


@RestController
@RequestMapping("/oauth")
@Slf4j
public class OAuthController {

    @Autowired
    private LinkedInOAuthService linkedInOAuthService;

    @Autowired
    private YouTubeOAuthService youtubeOAuthService;

    @Autowired
    private XOAuthService xOAuthService;

    @Autowired
    private InstagramOAuthService instagramOAuthService;

    @Autowired
    private FacebookOAuthService facebookOAuthService;

    @Autowired
    private OAuthSessionService oAuthSessionService;

    @Autowired
    private JedisPool jedisPool;


    @PostMapping("/x/callback")
    public ResponseEntity<String> handleCallback(
            @RequestBody XOAuthCallbackRequest request
    ) {
        xOAuthService.handleCallback(request);
        return ResponseEntity.ok("X connected");
    }




    @PostMapping("/linkedin/callback")
    public ResponseEntity<String> handleLinkedinCallback(@RequestBody Map<String, String> body) {
        linkedInOAuthService.exchangeCodeForToken(body.get("code"));
        return ResponseEntity.ok("LinkedIn connected");
    }

    @PostMapping("/youtube/callback")
    public ResponseEntity<?> youtubeCallback(@RequestBody Map<String, String> body) {
        try {
            youtubeOAuthService.exchangeCodeForTokens(body.get("code"));
            return ResponseEntity.ok("YouTube connected");
        } catch (Exception exp) {
            log.error("Youtube Callback Url Failed: {}", exp.getMessage(), exp);
            throw new RuntimeException(exp);
        }
    }

    @PostMapping("/instagram/callback")
    public ResponseEntity<?> instagramCallback(
            @RequestBody Map<String, String> body
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        instagramOAuthService.handleCallback(body.get("code"), userId);
        return ResponseEntity.ok("Instagram connected");
    }

    @PostMapping("/facebook/callback")
    public ResponseEntity<?> facebookCallback(
            @RequestBody Map<String, String> body
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        facebookOAuthService.handleCallback(body.get("code"), userId);
        return ResponseEntity.ok("Facebook connected");
    }


    // Add to OAuthController
    @PostMapping("/x/store-pkce")
    public ResponseEntity<Map<String, String>> storePkce(@RequestBody Map<String, String> data) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        String state = UUID.randomUUID().toString();
        String verifier = data.get("verifier");

        // Store in Redis with 10 min expiry (or use in-memory map)
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(userId+":"+"x_pkce:" + state, 10 * 60, verifier); // expiry in seconds
        }


        Map<String, String> response = new HashMap<>();
        response.put("state", state);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/x/get-pkce")
    public ResponseEntity<Map<String, String>> getPkce(@RequestParam String state) {
        if (state == null || state.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "state is required"));
        }

        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());

        String key = userId + ":" + "x_pkce:" + state;
        String verifier;

        try (Jedis jedis = jedisPool.getResource()) {
            try {
                verifier = jedis.getDel(key);
            } catch (NoSuchMethodError | UnsupportedOperationException e) {
                // Fallback: atomic GET + DEL via Lua script
                String lua = "local v = redis.call('GET', KEYS[1]); " +
                        "if v then redis.call('DEL', KEYS[1]); end; " +
                        "return v;";
                Object result = jedis.eval(lua, Collections.singletonList(key), Collections.emptyList());
                verifier = result == null ? null : Objects.toString(result);
            }
        }

        if (verifier == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, String> response = new HashMap<>();
        response.put("verifier", verifier);
        return ResponseEntity.ok(response);
    }

}
