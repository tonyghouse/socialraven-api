package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.service.profile.ProfileService;
import com.ghouse.socialraven.util.SecurityContextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/account-profiles")
public class AccountProfileController {

    @Autowired
    private  ProfileService profileService;

    @GetMapping("/connected")
    public List<ConnectedAccount> getConnectedAccounts(
            @RequestParam(value = "platform", required = false) Platform platform
    ) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        List<ConnectedAccount> connectedAccounts = new ArrayList<>();
        if (platform == null) {
            connectedAccounts = profileService.getAllConnectedAccounts(userId);
        } else {
            connectedAccounts = profileService.getConnectedAccounts(userId, platform);
        }

//        List<ConnectedAccount> duplicates = new ArrayList<>();
//
//        for (ConnectedAccount acc : connectedAccounts) {
//            for (int i = 0; i < 10; i++) {
//                ConnectedAccount copy = new ConnectedAccount();
//                BeanUtils.copyProperties(acc, copy);  // deep copy fields
//                copy.setProviderUserId(UUID.randomUUID().toString()); // unique ID
//                duplicates.add(copy);
//            }
//        }

        return connectedAccounts;
    }


    @GetMapping("/connected/all")
    public List<ConnectedAccount> getAllConnectedAccounts() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        List<ConnectedAccount> connectedAccounts = profileService.getAllConnectedAccounts(userId);
        return connectedAccounts;
    }


}
