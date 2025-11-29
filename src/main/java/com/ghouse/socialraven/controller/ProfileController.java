package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.service.profile.ProfileService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/profiles")
public class ProfileController {

    @Autowired
    private  ProfileService profileService;

    @GetMapping("/connected")
    public List<ConnectedAccount> getConnectedAccounts(
            @RequestParam(value = "platform", required = false) Platform platform
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return profileService.getConnectedAccounts(userId, platform);
    }


    @GetMapping("/connected/all")
    public List<ConnectedAccount> getAllConnectedAccounts() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return profileService.getAllConnectedAccounts(userId);
    }


}
