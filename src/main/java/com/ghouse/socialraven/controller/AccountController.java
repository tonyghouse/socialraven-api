package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.dto.ConnectedAccountCountDto;
import com.ghouse.socialraven.dto.ConnectedAccountDto;
import com.ghouse.socialraven.service.AccountService;
import com.ghouse.socialraven.util.SecurityContextUtil;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }


    @GetMapping
    @RequestMapping("/connected")
    public List<ConnectedAccountDto> getConnectedAccounts() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return accountService.getConnectedAccounts(userId);
    }

    @GetMapping
    @RequestMapping("/connected/count")
    public List<ConnectedAccountCountDto> getConnectedAccountCounts() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return accountService.getConnectedAccountCounts(userId);
    }


}
