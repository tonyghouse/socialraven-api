package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.dto.XOAuthCallbackRequest;
import com.ghouse.socialraven.service.provider.FacebookOAuthService;
import com.ghouse.socialraven.service.provider.InstagramOAuthService;
import com.ghouse.socialraven.service.provider.LinkedInOAuthService;
import com.ghouse.socialraven.service.provider.XOAuthService;
import com.ghouse.socialraven.service.provider.YouTubeOAuthService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


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


}
