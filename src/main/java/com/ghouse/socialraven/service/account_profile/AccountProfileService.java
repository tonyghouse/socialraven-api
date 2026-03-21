package com.ghouse.socialraven.service.account_profile;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.helper.AllowedPostTypeFetcher;
import com.ghouse.socialraven.mapper.ProviderPlatformMapper;
import com.ghouse.socialraven.repo.OAuthInfoRepo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ghouse.socialraven.repo.PostCollectionRepo;
import com.ghouse.socialraven.service.provider.InstagramOAuthService;
import com.ghouse.socialraven.service.provider.XOAuthService;
import com.ghouse.socialraven.service.provider.YouTubeOAuthService;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPool;


@Service
@Slf4j
public class AccountProfileService {

    @Autowired
    private OAuthInfoRepo oauthInfoRepo;

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
    private InstagramProfileService instagramProfileService;

    @Autowired
    private InstagramOAuthService instagramOAuthService;

    @Autowired
    private Environment environment;

    @Autowired
    private PostCollectionRepo postRepo;

    public List<ConnectedAccount> getConnectedAccounts(String workspaceId, @Nonnull Platform platform) {
        Provider provider = ProviderPlatformMapper.getProviderByPlatform(platform);
        List<OAuthInfoEntity> authInfos = oauthInfoRepo.findAllByWorkspaceIdAndProvider(workspaceId, provider);
        return buildConnectedAccounts(workspaceId, authInfos);
    }

    public List<ConnectedAccount> getAllConnectedAccounts(String workspaceId) {
        List<OAuthInfoEntity> authInfos = oauthInfoRepo.findAllByWorkspaceId(workspaceId);
        return buildConnectedAccounts(workspaceId, authInfos);
    }

    private List<ConnectedAccount> buildConnectedAccounts(String workspaceId, List<OAuthInfoEntity> authInfos) {
        List<ConnectedAccount> connectedAccounts = new ArrayList<>();

        for (OAuthInfoEntity authInfo : authInfos) {
            String redisKey = workspaceId + ":" + authInfo.getProviderUserId();
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
            } else if (Provider.INSTAGRAM.equals(authInfo.getProvider())) {
                var validOAuthInfo = instagramOAuthService.getValidOAuthInfo(authInfo);
                connectedAccount = instagramProfileService.fetchProfile(validOAuthInfo);
            }

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

        connectedAccounts.forEach(a -> {
            a.setAllowedFormats(AllowedPostTypeFetcher.getAllowedPostTypes(a.getPlatform()));
        });

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
                    cached = null;
                }
            }
        }
        return cached;
    }

    private ConnectedAccount getExpiredAuthInfoModel(OAuthInfoEntity authInfo) {
        ConnectedAccount connected = new ConnectedAccount();
        connected.setProviderUserId(authInfo.getProviderUserId());
        Platform platform = ProviderPlatformMapper.getPlatformByProvider(authInfo.getProvider());
        connected.setPlatform(platform);
        connected.setUsername("RE AUTHORIZE" + platform.name() + " User: " + authInfo.getProviderUserId());
        connected.setProfilePicLink("");
        return connected;
    }

    private static ConnectedAccount mapToUnknownConnectedAccount(OAuthInfoEntity authInfo, String providerUserId) {
        ConnectedAccount connected = new ConnectedAccount();
        connected.setProviderUserId(providerUserId);
        Platform platform = ProviderPlatformMapper.getPlatformByProvider(authInfo.getProvider());
        connected.setPlatform(platform);
        connected.setUsername(platform.name() + " User: " + providerUserId);
        connected.setProfilePicLink(null);
        return connected;
    }

    public void deleteConnectedAccount(String workspaceId, String providerUserId) {
        OAuthInfoEntity oauthInfo = oauthInfoRepo.findByWorkspaceIdAndProviderUserId(workspaceId, providerUserId);
        if (oauthInfo == null) {
            throw new RuntimeException("Account info not found");
        }
        oauthInfoRepo.delete(oauthInfo);
    }
}
