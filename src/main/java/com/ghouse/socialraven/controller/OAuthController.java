package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.service.LinkedInService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequestMapping("/oauth")
public class OAuthController {

    @Autowired
    private LinkedInService linkedInService;

    @PostMapping("/linkedin/callback")
    public ResponseEntity<String> handleLinkedinCallback(@RequestBody Map<String, String> body) {
        linkedInService.exchangeCodeForToken(body.get("code"));
        return ResponseEntity.ok("LinkedIn connected");
    }
}
