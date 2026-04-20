package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.service.webhook.InstagramWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/webhooks/instagram")
@RequiredArgsConstructor
public class InstagramWebhookController {

    private final InstagramWebhookService instagramWebhookService;

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode") String mode,
            @RequestParam(name = "hub.verify_token") String verifyToken,
            @RequestParam(name = "hub.challenge") String challenge) {
        return ResponseEntity.ok(instagramWebhookService.verifySubscription(mode, verifyToken, challenge));
    }

    @PostMapping
    public ResponseEntity<String> receiveWebhook(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestBody String rawPayload) {
        instagramWebhookService.processEvent(rawPayload, signatureHeader);
        return ResponseEntity.ok("EVENT_RECEIVED");
    }
}
