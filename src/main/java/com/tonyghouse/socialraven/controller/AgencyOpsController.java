package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.dto.workspace.AgencyOpsResponse;
import com.tonyghouse.socialraven.service.workspace.AgencyOpsService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspaces/agency-ops")
public class AgencyOpsController {

    @Autowired
    private AgencyOpsService agencyOpsService;

    @GetMapping
    public AgencyOpsResponse getAgencyOps(@RequestParam(required = false) String workspaceId,
                                          @RequestParam(required = false) String approverUserId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String dueWindow,
                                          @RequestParam(required = false) String timezone) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return agencyOpsService.getAgencyOps(userId, workspaceId, approverUserId, status, dueWindow, timezone);
    }
}
