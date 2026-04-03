package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.annotation.RequiresRole;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.reporting.ClientReportLinkResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportScheduleResponse;
import com.tonyghouse.socialraven.dto.reporting.CreateClientReportLinkRequest;
import com.tonyghouse.socialraven.dto.reporting.CreateClientReportScheduleRequest;
import com.tonyghouse.socialraven.service.reporting.ClientReportService;
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
@RequestMapping("/client-reports")
public class ClientReportController {

    @Autowired
    private ClientReportService clientReportService;

    @RequiresRole(WorkspaceRole.EDITOR)
    @GetMapping("/links")
    public List<ClientReportLinkResponse> getLinks() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return clientReportService.getLinks(userId);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/links")
    public ClientReportLinkResponse createLink(@RequestBody(required = false) CreateClientReportLinkRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return clientReportService.createLink(userId, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @DeleteMapping("/links/{linkId}")
    public ResponseEntity<Void> revokeLink(@PathVariable String linkId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        clientReportService.revokeLink(userId, linkId);
        return ResponseEntity.noContent().build();
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @GetMapping("/schedules")
    public List<ClientReportScheduleResponse> getSchedules() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return clientReportService.getSchedules(userId);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @PostMapping("/schedules")
    public ClientReportScheduleResponse createSchedule(@RequestBody CreateClientReportScheduleRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return clientReportService.createSchedule(userId, request);
    }

    @RequiresRole(WorkspaceRole.EDITOR)
    @DeleteMapping("/schedules/{scheduleId}")
    public ResponseEntity<Void> deactivateSchedule(@PathVariable Long scheduleId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        clientReportService.deactivateSchedule(userId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
