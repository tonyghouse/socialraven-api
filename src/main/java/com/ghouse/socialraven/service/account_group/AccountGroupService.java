package com.ghouse.socialraven.service.account_group;

import com.ghouse.socialraven.dto.AccountGroupDto;
import com.ghouse.socialraven.entity.AccountGroupEntity;
import com.ghouse.socialraven.exception.SocialRavenException;
import com.ghouse.socialraven.repo.AccountGroupRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountGroupService {

    @Autowired
    private AccountGroupRepo accountGroupRepo;

    public List<AccountGroupDto> getAll(String userId) {
        return accountGroupRepo.findAllByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public AccountGroupDto create(String userId, String name, String color) {
        AccountGroupEntity entity = new AccountGroupEntity();
        entity.setUserId(userId);
        entity.setName(name);
        entity.setColor(color);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setAccountIds(new ArrayList<>());
        return toDto(accountGroupRepo.save(entity));
    }

    public AccountGroupDto update(String userId, Long id, String name, String color) {
        AccountGroupEntity entity = accountGroupRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new SocialRavenException("Group not found", HttpStatus.NOT_FOUND));
        entity.setName(name);
        entity.setColor(color);
        return toDto(accountGroupRepo.save(entity));
    }

    public void delete(String userId, Long id) {
        AccountGroupEntity entity = accountGroupRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new SocialRavenException("Group not found", HttpStatus.NOT_FOUND));
        accountGroupRepo.delete(entity);
    }

    @Transactional
    public void addAccountToGroup(String userId, Long groupId, String providerUserId) {
        // Remove from any existing group first (an account can only belong to one group)
        List<AccountGroupEntity> existing = accountGroupRepo.findByUserIdAndAccountIdMember(userId, providerUserId);
        for (AccountGroupEntity g : existing) {
            g.getAccountIds().remove(providerUserId);
            accountGroupRepo.save(g);
        }

        AccountGroupEntity target = accountGroupRepo.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new SocialRavenException("Group not found", HttpStatus.NOT_FOUND));
        if (!target.getAccountIds().contains(providerUserId)) {
            target.getAccountIds().add(providerUserId);
        }
        accountGroupRepo.save(target);
    }

    @Transactional
    public void removeAccountFromGroup(String userId, Long groupId, String providerUserId) {
        AccountGroupEntity entity = accountGroupRepo.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new SocialRavenException("Group not found", HttpStatus.NOT_FOUND));
        entity.getAccountIds().remove(providerUserId);
        accountGroupRepo.save(entity);
    }

    private AccountGroupDto toDto(AccountGroupEntity entity) {
        AccountGroupDto dto = new AccountGroupDto();
        dto.setId(String.valueOf(entity.getId()));
        dto.setName(entity.getName());
        dto.setColor(entity.getColor());
        dto.setAccountIds(new ArrayList<>(entity.getAccountIds()));
        dto.setCreatedAt(entity.getCreatedAt().toString());
        return dto;
    }
}
