package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.dto.XOAuthCallbackRequest;
import com.ghouse.socialraven.service.provider.LinkedInOAuthService;
import com.ghouse.socialraven.service.provider.XOAuthService;
import com.ghouse.socialraven.service.provider.YouTubeOAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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


    @PostMapping("/x/callback")
    public ResponseEntity<ConnectedAccount> handleCallback(
            @RequestBody XOAuthCallbackRequest request
    ) {
        ConnectedAccount dto = xOAuthService.handleCallback(request);
        return ResponseEntity.ok(dto);
    }


    @PostMapping("/linkedin/callback")
    public ResponseEntity<String> handleLinkedinCallback(@RequestBody Map<String, String> body) {
        linkedInOAuthService.exchangeCodeForToken(body.get("code"));
        return ResponseEntity.ok("LinkedIn connected");
    }

    @PostMapping("/youtube/callback")
    public ResponseEntity<?> youtubeCallback(@RequestBody Map<String, String> body) {
        try {
            log.info("Youtube callback request: {}",body);
            youtubeOAuthService.exchangeCodeForTokens(body.get("code"));
            return ResponseEntity.ok("YouTube connected");
        } catch (Exception exp) {
            log.error("Youtube Callback Url Failed: {}", exp.getMessage() ,exp);
            throw new RuntimeException(exp);
        }
    }





}
