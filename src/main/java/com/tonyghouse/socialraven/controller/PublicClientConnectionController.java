package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.dto.clientconnect.ClientConnectionActivityResponse;
import com.tonyghouse.socialraven.dto.clientconnect.PublicClientConnectionCallbackRequest;
import com.tonyghouse.socialraven.dto.clientconnect.PublicClientConnectionSessionResponse;
import com.tonyghouse.socialraven.service.clientconnect.ClientConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/client-connect")
public class PublicClientConnectionController {

    @Autowired
    private ClientConnectionService clientConnectionService;

    @GetMapping("/{token}")
    public PublicClientConnectionSessionResponse getSession(@PathVariable String token) {
        return clientConnectionService.getPublicSession(token);
    }

    @PostMapping("/{token}/{platform}/callback")
    public ClientConnectionActivityResponse completeConnection(@PathVariable String token,
                                                               @PathVariable Platform platform,
                                                               @RequestBody PublicClientConnectionCallbackRequest request) {
        return clientConnectionService.completePublicConnection(token, platform, request);
    }
}
