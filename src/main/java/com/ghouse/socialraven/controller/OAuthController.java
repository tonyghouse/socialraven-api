package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.service.LinkedInService;
import com.ghouse.socialraven.service.XService;
import com.ghouse.socialraven.service.YouTubeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private LinkedInService linkedInService;

    @Autowired
    private XService xService;

    @Autowired
    private YouTubeService youtubeService;


    @PostMapping("/linkedin/callback")
    public ResponseEntity<String> handleLinkedinCallback(@RequestBody Map<String, String> body) {
        linkedInService.exchangeCodeForToken(body.get("code"));
        return ResponseEntity.ok("LinkedIn connected");
    }

    @PostMapping("/youtube/callback")
    public ResponseEntity<?> youtubeCallback(@RequestBody Map<String, String> body) {
        try {
            log.info("Youtube callback request: {}",body);
            youtubeService.exchangeCodeForTokens(body.get("code"));
            return ResponseEntity.ok("YouTube connected");
        } catch (Exception exp) {
            log.error("Youtube Callback Url Failed: {}", exp.getMessage() ,exp);
            throw new RuntimeException(exp);
        }
    }


    @GetMapping("/x/request-token")
    public ResponseEntity<Map<String, String>> getXRequestToken() {
        try {
            Map<String, String> requestToken = xService.getXRequestToken();
            return ResponseEntity.ok(requestToken);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/x/access-token")
    public ResponseEntity<?> getXAccessToken(@RequestBody Map<String, String> body) throws Exception {
        try {
            String twitterUserId = xService.getXAccessToken(body);

            return ResponseEntity.ok(Map.of(
                    "status", "connected",
                    "userId", twitterUserId
            ));

        } catch (Exception exp) {
            log.error("X Callback Url Failed: {}", exp.getMessage() ,exp);
            return ResponseEntity.status(500).body(Map.of("error", exp.getMessage()));
        }
    }


}
