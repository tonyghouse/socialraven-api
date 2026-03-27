package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import com.tonyghouse.socialraven.util.WorkspaceContext;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/account-profiles")
public class AccountProfileController {

    @Autowired
    private AccountProfileService accountProfileService;

    @GetMapping("/connected")
    public List<ConnectedAccount> getConnectedAccounts(
            @RequestParam(value = "platform", required = false) Platform platform
    ) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        if (platform == null) {
            return accountProfileService.getAllConnectedAccounts(workspaceId);
        } else {
            return accountProfileService.getConnectedAccounts(workspaceId, platform);
        }
    }

    @GetMapping("/connected/all")
    public List<ConnectedAccount> getAllConnectedAccounts() {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        return accountProfileService.getAllConnectedAccounts(workspaceId);
    }

    @GetMapping("/connected/delete/{providerUserId}")
    public String deleteConnectedAccount(@PathVariable String providerUserId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        accountProfileService.deleteConnectedAccount(workspaceId, providerUserId);
        return "SUCCESS";
    }
}
