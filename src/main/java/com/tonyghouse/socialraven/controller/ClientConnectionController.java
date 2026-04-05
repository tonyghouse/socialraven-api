package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.annotation.RequiresRole;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.clientconnect.ClientConnectionSessionResponse;
import com.tonyghouse.socialraven.dto.clientconnect.CreateClientConnectionSessionRequest;
import com.tonyghouse.socialraven.service.clientconnect.ClientConnectionService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/client-connect")
public class ClientConnectionController {

    @Autowired
    private ClientConnectionService clientConnectionService;

    @RequiresRole(WorkspaceRole.EDITOR)
    @GetMapping("/sessions")
    public List<ClientConnectionSessionResponse> getSessions() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return clientConnectionService.getSessions(userId);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/sessions")
    public ClientConnectionSessionResponse createSession(
            @RequestBody CreateClientConnectionSessionRequest request
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return clientConnectionService.createSession(userId, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        clientConnectionService.revokeSession(userId, sessionId);
        return ResponseEntity.noContent().build();
    }
}
