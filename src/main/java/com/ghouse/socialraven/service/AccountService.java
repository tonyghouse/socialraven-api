package com.ghouse.socialraven.service;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.dto.ConnectedAccountCountDto;
import com.ghouse.socialraven.dto.ConnectedAccountDto;
import com.ghouse.socialraven.repo.OAuthTokenRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AccountService {

    private final OAuthTokenRepository oAuthTokenRepo;

    public AccountService(OAuthTokenRepository oAuthTokenRepo) {
        this.oAuthTokenRepo = oAuthTokenRepo;
    }

    public List<ConnectedAccountDto> getConnectedAccounts(String userId) {
        List<ConnectedAccountDto> list = new ArrayList<>();
        ConnectedAccountDto connectedAccountDto = new ConnectedAccountDto();
        connectedAccountDto.setId("12345");
        connectedAccountDto.setPlatform(Platform.linkedin);
        connectedAccountDto.setUsername("kinguser");
        list.add(connectedAccountDto);

        return list;
    }

    public List<ConnectedAccountCountDto> getConnectedAccountCounts(String userId) {
        List<ConnectedAccountCountDto> list = new ArrayList<>();
        ConnectedAccountCountDto connectedAccountCountDto = new ConnectedAccountCountDto();
        connectedAccountCountDto.setPlatform(Platform.linkedin);
        connectedAccountCountDto.setCount(1);

        list.add(connectedAccountCountDto);

        return list;
    }
}
