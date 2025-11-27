package com.ghouse.socialraven.service.profile;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.mapper.ProviderPlatformMapper;
import com.ghouse.socialraven.repo.OAuthInfoRepo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ghouse.socialraven.service.provider.XOAuthService;
import com.ghouse.socialraven.service.provider.YouTubeOAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPool;


@Service
public class ProfileService {

    @Autowired
    private OAuthInfoRepo repo;

    @Autowired
    private LinkedInProfileService linkedInProfileService;

    @Autowired
    private XProfileService xProfileService;

    @Autowired
    private YouTubeProfileService youTubeProfileService;

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private XOAuthService xoAuthService;

    @Autowired
    private YouTubeOAuthService youTubeOAuthService;

    @Autowired
    private Environment environment;


    public List<ConnectedAccount> getConnectedAccounts(String userId, Platform platform) {
        List<OAuthInfoEntity> authInfos = getOAuthInfos(userId, platform);
        List<ConnectedAccount> connectedAccounts = new ArrayList<>();

        for (OAuthInfoEntity authInfo : authInfos) {
            String redisKey = userId + ":" + authInfo.getProviderUserId();
            ConnectedAccount cached = getConnectedAccountFromCache(redisKey);
            if (cached != null) {
                connectedAccounts.add(cached);
                continue;
            }


            ConnectedAccount connectedAccount = null;
            if (Provider.X.equals(authInfo.getProvider())) {
                boolean isProd = Arrays.asList(environment.getActiveProfiles())
                        .contains("prod");
                if (isProd) {
                    var validOAuthInfo = xoAuthService.getValidOAuthInfo(authInfo);
                    connectedAccount = xProfileService.fetchProfile(validOAuthInfo);
                } else {
                    connectedAccount = null;
                }
            } else if (Provider.LINKEDIN.equals(authInfo.getProvider())) {
                long now = System.currentTimeMillis();
                if (authInfo.getExpiresAt() > now) {
                    connectedAccount = linkedInProfileService.fetchProfile(authInfo);
                } else {
                    connectedAccount = getExpiredAuthInfoModel(authInfo);
                }

            } else if (Provider.YOUTUBE.equals(authInfo.getProvider())) {
                var validOAuthInfo = youTubeOAuthService.getValidOAuthInfo(authInfo);
                connectedAccount = youTubeProfileService.fetchProfile(validOAuthInfo);
            }

            // If provider returned valid data → STORE IN REDIS for 24 hours
            if (connectedAccount != null) {
                connectedAccounts.add(connectedAccount);
                try (var jedis = jedisPool.getResource()) {
                    jedis.setex(redisKey, 24 * 60 * 60, connectedAccount.toJson());
                }
            } else {
                ConnectedAccount connected = mapToUnknownConnectedAccount(authInfo, authInfo.getProviderUserId());
                connectedAccounts.add(connected);
            }
        }

        return connectedAccounts;
    }

    private ConnectedAccount getConnectedAccountFromCache(String redisKey) {
        ConnectedAccount cached = null;
        try (var jedis = jedisPool.getResource()) {
            String redisValue = jedis.get(redisKey);

            if (redisValue != null) {
                try {
                    cached = ConnectedAccount.fromJson(redisValue);
                } catch (Exception ex) {
                    // Parsing failed → treat as null
                    cached = null;
                }
            }
        }
        return cached;
    }

    private List<OAuthInfoEntity> getOAuthInfos(String userId, Platform platform) {
        Provider provider = ProviderPlatformMapper.getProviderByPlatform(platform);
        List<OAuthInfoEntity> authInfos;
        if (provider != null) {
            authInfos = repo.findAllByUserIdAndProvider(userId, provider);
        } else {
            authInfos = repo.findAllByUserId(userId);
        }
        return authInfos;
    }

    private ConnectedAccount getExpiredAuthInfoModel(OAuthInfoEntity authInfo) {
        ConnectedAccount connected = new ConnectedAccount();
        connected.setProviderUserId(authInfo.getProviderUserId());
        connected.setPlatform(ProviderPlatformMapper.getPlatformByProvider(authInfo.getProvider()));
        connected.setUsername("RE AUTHORIZE");
        connected.setProfilePicLink("");
        return connected;
    }


    private static ConnectedAccount mapToUnknownConnectedAccount(OAuthInfoEntity authInfo, String providerUserId) {
        ConnectedAccount connected = new ConnectedAccount();
        connected.setProviderUserId(providerUserId);
        connected.setPlatform(ProviderPlatformMapper.getPlatformByProvider(authInfo.getProvider()));
        connected.setUsername("UNKNOWN");
        connected.setProfilePicLink(null);
        return connected;
    }

}
