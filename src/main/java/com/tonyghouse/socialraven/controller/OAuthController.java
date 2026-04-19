package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.annotation.RequiresRole;
import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.XOAuthCallbackRequest;
import com.tonyghouse.socialraven.service.ConnectionFailureAlertService;
import com.tonyghouse.socialraven.service.provider.FacebookOAuthService;
import com.tonyghouse.socialraven.service.provider.InstagramOAuthService;
import com.tonyghouse.socialraven.service.provider.LinkedInOAuthService;
import com.tonyghouse.socialraven.service.provider.TikTokOAuthService;
import com.tonyghouse.socialraven.service.provider.ThreadsOAuthService;
import com.tonyghouse.socialraven.service.provider.XOAuthService;
import com.tonyghouse.socialraven.service.provider.YouTubeOAuthService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
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
    private ThreadsOAuthService threadsOAuthService;

    @Autowired
    private TikTokOAuthService tikTokOAuthService;

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private ConnectionFailureAlertService connectionFailureAlertService;


    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/x/callback")
    public ResponseEntity<String> handleCallback(
            @RequestBody XOAuthCallbackRequest request
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return runWorkspaceConnectionCallback(Platform.x, userId, () -> xOAuthService.handleCallback(request), "X connected");
    }




    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/linkedin/callback")
    public ResponseEntity<String> handleLinkedinCallback(@RequestBody Map<String, String> body) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return runWorkspaceConnectionCallback(
                Platform.linkedin,
                userId,
                () -> linkedInOAuthService.exchangeCodeForToken(body.get("code")),
                "LinkedIn connected"
        );
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/youtube/callback")
    public ResponseEntity<?> youtubeCallback(@RequestBody Map<String, String> body) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return runWorkspaceConnectionCallback(
                Platform.youtube,
                userId,
                () -> youtubeOAuthService.exchangeCodeForTokens(body.get("code")),
                "YouTube connected"
        );
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/instagram/callback")
    public ResponseEntity<?> instagramCallback(
            @RequestBody Map<String, String> body
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return runWorkspaceConnectionCallback(
                Platform.instagram,
                userId,
                () -> instagramOAuthService.handleCallback(body.get("code"), userId),
                "Instagram connected"
        );
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/facebook/callback")
    public ResponseEntity<?> facebookCallback(
            @RequestBody Map<String, String> body
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return runWorkspaceConnectionCallback(
                Platform.facebook,
                userId,
                () -> facebookOAuthService.handleCallback(body.get("code"), userId),
                "Facebook connected"
        );
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/threads/callback")
    public ResponseEntity<?> threadsCallback(
            @RequestBody Map<String, String> body
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return runWorkspaceConnectionCallback(
                Platform.threads,
                userId,
                () -> threadsOAuthService.handleCallback(body.get("code"), userId),
                "Threads connected"
        );
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/tiktok/callback")
    public ResponseEntity<?> tikTokCallback(
            @RequestBody Map<String, String> body
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return runWorkspaceConnectionCallback(
                Platform.tiktok,
                userId,
                () -> tikTokOAuthService.handleCallback(body.get("code"), userId),
                "TikTok connected"
        );
    }


    // Add to OAuthController
    @RequiresRole(WorkspaceRole.EDITOR)
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

    private ResponseEntity<String> runWorkspaceConnectionCallback(Platform platform,
                                                                  String userId,
                                                                  ThrowingRunnable callback,
                                                                  String successMessage) {
        try {
            callback.run();
            return ResponseEntity.ok(successMessage);
        } catch (Exception exception) {
            log.error("{} OAuth callback failed for userId={}", platform, userId, exception);
            connectionFailureAlertService.notifyWorkspaceConnectionFailure(platform, userId, exception);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

}
