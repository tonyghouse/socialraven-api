package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.dto.AccountGroupDto;
import com.ghouse.socialraven.dto.AccountGroupRequest;
import com.ghouse.socialraven.service.account_group.AccountGroupService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/account-groups")
public class AccountGroupController {

    @Autowired
    private AccountGroupService accountGroupService;

    /**
     * GET /account-groups
     * Returns all account groups for the authenticated user.
     */
    @GetMapping
    public List<AccountGroupDto> getAll() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return accountGroupService.getAll(userId);
    }

    /**
     * POST /account-groups
     * Body: { name, color }
     * Creates a new account group for the authenticated user.
     */
    @PostMapping
    public AccountGroupDto create(@RequestBody AccountGroupRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return accountGroupService.create(userId, request.getName(), request.getColor());
    }

    /**
     * PUT /account-groups/{id}
     * Body: { name, color }
     * Updates the name and/or color of an existing group.
     */
    @PutMapping("/{id}")
    public AccountGroupDto update(@PathVariable Long id, @RequestBody AccountGroupRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return accountGroupService.update(userId, id, request.getName(), request.getColor());
    }

    /**
     * DELETE /account-groups/{id}
     * Deletes the group; member accounts become ungrouped.
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        accountGroupService.delete(userId, id);
    }

    /**
     * PUT /account-groups/{groupId}/accounts/{providerUserId}
     * Assigns the account to the group; removes it from any previous group.
     */
    @PutMapping("/{groupId}/accounts/{providerUserId}")
    public void addAccount(@PathVariable Long groupId, @PathVariable String providerUserId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        accountGroupService.addAccountToGroup(userId, groupId, providerUserId);
    }

    /**
     * DELETE /account-groups/{groupId}/accounts/{providerUserId}
     * Removes the account from the group; account becomes ungrouped.
     */
    @DeleteMapping("/{groupId}/accounts/{providerUserId}")
    public void removeAccount(@PathVariable Long groupId, @PathVariable String providerUserId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        accountGroupService.removeAccountFromGroup(userId, groupId, providerUserId);
    }
}
