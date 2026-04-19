package com.tonyghouse.socialraven.service.account_profile;

import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.helper.AllowedPostTypeFetcher;
import com.tonyghouse.socialraven.mapper.ProviderPlatformMapper;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.tonyghouse.socialraven.service.provider.InstagramOAuthService;
import com.tonyghouse.socialraven.service.provider.FacebookOAuthService;
import com.tonyghouse.socialraven.service.provider.TikTokOAuthService;
import com.tonyghouse.socialraven.service.provider.ThreadsOAuthService;
import com.tonyghouse.socialraven.service.provider.XOAuthService;
import com.tonyghouse.socialraven.service.provider.YouTubeOAuthService;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private FacebookProfileService facebookProfileService;

    @Autowired
    private InstagramOAuthService instagramOAuthService;

    @Autowired
    private FacebookOAuthService facebookOAuthService;

    @Autowired
    private ThreadsProfileService threadsProfileService;

    @Autowired
    private TikTokProfileService tikTokProfileService;

    @Autowired
    private ThreadsOAuthService threadsOAuthService;

    @Autowired
    private TikTokOAuthService tikTokOAuthService;

    @Autowired
    private Environment environment;

    @Value("${socialraven.connected-account.cache.success-ttl-seconds:43200}")
    private int connectedAccountSuccessTtlSeconds;

    @Value("${socialraven.connected-account.cache.failure-ttl-seconds:300}")
    private int connectedAccountFailureTtlSeconds;

    public List<ConnectedAccount> getConnectedAccounts(String workspaceId, @Nonnull Platform platform) {
        return getConnectedAccounts(workspaceId, platform, true);
    }

    public List<ConnectedAccount> getConnectedAccounts(String workspaceId,
                                                       @Nonnull Platform platform,
                                                       boolean allowRemoteFetch) {
        Provider provider = ProviderPlatformMapper.getProviderByPlatform(platform);
        List<OAuthInfoEntity> authInfos = oauthInfoRepo.findAllByWorkspaceIdAndProvider(workspaceId, provider);
        return buildConnectedAccounts(workspaceId, authInfos, allowRemoteFetch);
    }

    public List<ConnectedAccount> getAllConnectedAccounts(String workspaceId) {
        return getAllConnectedAccounts(workspaceId, true);
    }

    public List<ConnectedAccount> getAllConnectedAccounts(String workspaceId, boolean allowRemoteFetch) {
        List<OAuthInfoEntity> authInfos = oauthInfoRepo.findAllByWorkspaceId(workspaceId);
        return buildConnectedAccounts(workspaceId, authInfos, allowRemoteFetch);
    }

    private List<ConnectedAccount> buildConnectedAccounts(String workspaceId,
                                                          List<OAuthInfoEntity> authInfos,
                                                          boolean allowRemoteFetch) {
        if (authInfos.isEmpty()) {
            return List.of();
        }

        List<ConnectedAccount> connectedAccounts =
                new ArrayList<>(Collections.nCopies(authInfos.size(), null));
        List<Integer> missingIndexes = new ArrayList<>();

        for (int i = 0; i < authInfos.size(); i++) {
            OAuthInfoEntity authInfo = authInfos.get(i);
            String redisKey = connectedAccountCacheKey(workspaceId, authInfo);
            String legacyRedisKey = legacyConnectedAccountCacheKey(workspaceId, authInfo);
            ConnectedAccount cached = getConnectedAccountFromCache(redisKey, legacyRedisKey);
            if (cached != null) {
                applyAllowedFormats(cached);
                connectedAccounts.set(i, cached);
                continue;
            }

            missingIndexes.add(i);
        }

        if (allowRemoteFetch) {
            resolveMissingAccountsWithVirtualThreads(workspaceId, authInfos, missingIndexes, connectedAccounts);
        } else {
            for (Integer index : missingIndexes) {
                OAuthInfoEntity authInfo = authInfos.get(index);
                ConnectedAccount fallback = buildFallbackConnectedAccount(authInfo);
                applyAllowedFormats(fallback);
                connectedAccounts.set(index, fallback);
            }
        }

        return connectedAccounts;
    }

    private void resolveMissingAccountsWithVirtualThreads(String workspaceId,
                                                          List<OAuthInfoEntity> authInfos,
                                                          List<Integer> missingIndexes,
                                                          List<ConnectedAccount> connectedAccounts) {
        if (missingIndexes.isEmpty()) {
            return;
        }

        List<Future<ResolvedConnectedAccount>> futures = new ArrayList<>(missingIndexes.size());
        try (ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (Integer index : missingIndexes) {
                OAuthInfoEntity authInfo = authInfos.get(index);
                String redisKey = connectedAccountCacheKey(workspaceId, authInfo);
                futures.add(executor.submit(new ResolveConnectedAccountTask(index, authInfo, redisKey)));
            }

            for (Future<ResolvedConnectedAccount> future : futures) {
                ResolvedConnectedAccount resolved = future.get();
                connectedAccounts.set(resolved.index(), resolved.connectedAccount());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Connected-account resolution interrupted, falling back to local placeholders.");
            fillFallbacksForMissing(authInfos, missingIndexes, connectedAccounts);
        } catch (ExecutionException ex) {
            log.warn("Connected-account parallel resolution failed, falling back to local placeholders: {}",
                    ex.getMessage());
            fillFallbacksForMissing(authInfos, missingIndexes, connectedAccounts);
        }
    }

    private void fillFallbacksForMissing(List<OAuthInfoEntity> authInfos,
                                         List<Integer> missingIndexes,
                                         List<ConnectedAccount> connectedAccounts) {
        for (Integer index : missingIndexes) {
            if (connectedAccounts.get(index) != null) {
                continue;
            }
            ConnectedAccount fallback = buildFallbackConnectedAccount(authInfos.get(index));
            applyAllowedFormats(fallback);
            connectedAccounts.set(index, fallback);
        }
    }

    private class ResolveConnectedAccountTask implements Callable<ResolvedConnectedAccount> {
        private final int index;
        private final OAuthInfoEntity authInfo;
        private final String redisKey;

        private ResolveConnectedAccountTask(int index, OAuthInfoEntity authInfo, String redisKey) {
            this.index = index;
            this.authInfo = authInfo;
            this.redisKey = redisKey;
        }

        @Override
        public ResolvedConnectedAccount call() {
            ConnectedAccount connectedAccount = fetchConnectedAccountFromProvider(authInfo);
            boolean fetchedFromProvider = connectedAccount != null;

            if (!fetchedFromProvider) {
                connectedAccount = buildFallbackConnectedAccount(authInfo);
            }

            applyAllowedFormats(connectedAccount);

            if (fetchedFromProvider) {
                cacheConnectedAccount(redisKey, connectedAccount, connectedAccountSuccessTtlSeconds);
            } else {
                cacheConnectedAccount(redisKey, connectedAccount, connectedAccountFailureTtlSeconds);
            }

            return new ResolvedConnectedAccount(index, connectedAccount);
        }
    }

    private record ResolvedConnectedAccount(int index, ConnectedAccount connectedAccount) {}

    private void applyAllowedFormats(ConnectedAccount connectedAccount) {
        connectedAccount.setAllowedFormats(
                AllowedPostTypeFetcher.getAllowedPostTypes(connectedAccount.getPlatform())
        );
    }

    private ConnectedAccount getConnectedAccountFromCache(String redisKey, String legacyRedisKey) {
        ConnectedAccount cached = getConnectedAccountFromSingleCacheKey(redisKey);
        if (cached != null) {
            return cached;
        }

        ConnectedAccount legacy = getConnectedAccountFromSingleCacheKey(legacyRedisKey);
        if (legacy != null) {
            // Promote old key format into the new provider-safe key format.
            cacheConnectedAccount(redisKey, legacy, connectedAccountSuccessTtlSeconds);
            return legacy;
        }
        return null;
    }

    private ConnectedAccount getConnectedAccountFromSingleCacheKey(String redisKey) {
        try (var jedis = jedisPool.getResource()) {
            String redisValue = jedis.get(redisKey);
            if (redisValue != null) {
                try {
                    return ConnectedAccount.fromJson(redisValue);
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        return null;
    }

    private ConnectedAccount fetchConnectedAccountFromProvider(OAuthInfoEntity authInfo) {
        try {
            if (Provider.X.equals(authInfo.getProvider())) {
                boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
                if (isProd) {
                    var validOAuthInfo = xoAuthService.getValidOAuthInfo(authInfo);
                    return xProfileService.fetchProfile(validOAuthInfo);
                }
                return null;
            }

            if (Provider.LINKEDIN.equals(authInfo.getProvider())) {
                long now = System.currentTimeMillis();
                if (authInfo.getExpiresAt() > now) {
                    return linkedInProfileService.fetchProfile(authInfo);
                }
                return getExpiredAuthInfoModel(authInfo);
            }

            if (Provider.YOUTUBE.equals(authInfo.getProvider())) {
                var validOAuthInfo = youTubeOAuthService.getValidOAuthInfo(authInfo);
                return youTubeProfileService.fetchProfile(validOAuthInfo);
            }

            if (Provider.INSTAGRAM.equals(authInfo.getProvider())) {
                var validOAuthInfo = instagramOAuthService.getValidOAuthInfo(authInfo);
                return instagramProfileService.fetchProfile(validOAuthInfo);
            }

            if (Provider.FACEBOOK.equals(authInfo.getProvider())) {
                var validOAuthInfo = facebookOAuthService.getValidOAuthInfo(authInfo);
                return facebookProfileService.fetchProfile(validOAuthInfo);
            }

            if (Provider.TIKTOK.equals(authInfo.getProvider())) {
                var validOAuthInfo = tikTokOAuthService.getValidOAuthInfo(authInfo);
                return tikTokProfileService.fetchProfile(validOAuthInfo);
            }

            if (Provider.THREADS.equals(authInfo.getProvider())) {
                var validOAuthInfo = threadsOAuthService.getValidOAuthInfo(authInfo);
                return threadsProfileService.fetchProfile(validOAuthInfo);
            }
        } catch (Exception ex) {
            log.warn("Provider profile resolution failed for provider={}, providerUserId={}: {}",
                    authInfo.getProvider(), authInfo.getProviderUserId(), ex.getMessage());
        }

        return null;
    }

    private ConnectedAccount buildFallbackConnectedAccount(OAuthInfoEntity authInfo) {
        long now = System.currentTimeMillis();
        if (authInfo.getExpiresAt() > 0 && authInfo.getExpiresAt() <= now) {
            return getExpiredAuthInfoModel(authInfo);
        }
        return mapToUnknownConnectedAccount(authInfo, authInfo.getProviderUserId());
    }

    private void cacheConnectedAccount(String redisKey, ConnectedAccount connectedAccount, int ttlSeconds) {
        try (var jedis = jedisPool.getResource()) {
            jedis.setex(redisKey, Math.max(ttlSeconds, 1), connectedAccount.toJson());
        } catch (Exception ex) {
            log.debug("Connected-account cache write failed for key={}", redisKey, ex);
        }
    }

    private void evictConnectedAccountCache(String workspaceId, OAuthInfoEntity authInfo) {
        String redisKey = connectedAccountCacheKey(workspaceId, authInfo);
        String legacyRedisKey = legacyConnectedAccountCacheKey(workspaceId, authInfo);
        try (var jedis = jedisPool.getResource()) {
            jedis.del(redisKey, legacyRedisKey);
        } catch (Exception ex) {
            log.debug("Connected-account cache eviction failed for providerUserId={}",
                    authInfo.getProviderUserId(), ex);
        }
    }

    private String connectedAccountCacheKey(String workspaceId, OAuthInfoEntity authInfo) {
        return "cache:connected-account:v2:"
                + workspaceId + ":"
                + authInfo.getProvider().name() + ":"
                + authInfo.getProviderUserId();
    }

    private String legacyConnectedAccountCacheKey(String workspaceId, OAuthInfoEntity authInfo) {
        return workspaceId + ":" + authInfo.getProviderUserId();
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
        evictConnectedAccountCache(workspaceId, oauthInfo);
    }
}
