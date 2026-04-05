package com.tonyghouse.socialraven.service.provider;

import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientConnectionSessionEntity;
import com.tonyghouse.socialraven.helper.RedisTokenExpirySaver;
import com.tonyghouse.socialraven.model.AdditionalOAuthInfo;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.service.clientconnect.OAuthConnectionPersistenceService;
import com.tonyghouse.socialraven.service.clientconnect.OAuthConnectionPersistenceService.PersistedConnection;
import com.tonyghouse.socialraven.util.TimeUtil;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class FacebookOAuthService {

    private static final Logger log = LoggerFactory.getLogger(FacebookOAuthService.class);

    @Value("${facebook.app.id}")
    private String appId;

    @Value("${facebook.app.secret}")
    private String appSecret;

    @Value("${facebook.redirect.uri}")
    private String redirectUri;

    @Autowired
    private OAuthInfoRepo repo;

    @Autowired
    private RestTemplate rest;

    @Autowired
    private RedisTokenExpirySaver redisTokenExpirySaver;

    public void handleCallback(String code, String userId) {
        handleCallback(code, userId, WorkspaceContext.getWorkspaceId(), null, null, null);
    }

    @Autowired
    private OAuthConnectionPersistenceService oauthConnectionPersistenceService;

    public PersistedConnection exchangeCodeForClientConnection(String code,
                                                               WorkspaceClientConnectionSessionEntity session,
                                                               String ownerDisplayName,
                                                               String ownerEmail) {
        return handleCallback(
                code,
                session.getCreatedByUserId(),
                session.getWorkspaceId(),
                session.getId(),
                ownerDisplayName,
                ownerEmail
        );
    }

    private PersistedConnection handleCallback(String code,
                                               String managingUserId,
                                               String workspaceId,
                                               String sessionId,
                                               String ownerDisplayName,
                                               String ownerEmail) {
        log.info("Starting Facebook OAuth callback for userId: {}", managingUserId);
        log.debug("Received code: {}", code);

        try {
            // STEP 1: Exchange code → short-lived access token
            log.info("Step 1: Exchanging code for short-lived token");
            Map<String, Object> shortToken = exchangeShortLivedToken(code);
            String shortAccessToken = (String) shortToken.get("access_token");
            log.info("Successfully obtained short-lived access token");

            // STEP 2: Exchange short-lived → long-lived token
            log.info("Step 2: Exchanging for long-lived token");
            Map<String, Object> longToken = exchangeLongLivedToken(shortAccessToken);
            String longAccessToken = (String) longToken.get("access_token");
            Integer expiresIn = (Integer) longToken.get("expires_in");
            log.info("Successfully obtained long-lived token. Expires in: {} seconds", expiresIn);

            // STEP 3: Get Facebook User ID
            log.info("Step 3: Fetching Facebook user ID");
            String fbUserId = fetchFacebookUserId(longAccessToken);
            log.info("Facebook user ID fetched: {}", fbUserId);

            // STEP 4: Get Facebook Pages
            log.info("Step 4: Fetching Facebook Pages");
            List<Map<String, Object>> pages = fetchFacebookPages(longAccessToken);
            log.info("Found {} Facebook Pages", pages != null ? pages.size() : 0);

            long expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
            AdditionalOAuthInfo additionalOAuthInfo = new AdditionalOAuthInfo();
            PersistedConnection saved;
            if (sessionId != null) {
                saved = oauthConnectionPersistenceService.saveClientConnection(
                        workspaceId,
                        managingUserId,
                        sessionId,
                        ownerDisplayName,
                        ownerEmail,
                        Provider.FACEBOOK,
                        fbUserId,
                        longAccessToken,
                        TimeUtil.toUTCOffsetDateTime(expiresAt),
                        additionalOAuthInfo
                );
            } else {
                saved = oauthConnectionPersistenceService.saveWorkspaceMemberConnection(
                        workspaceId,
                        managingUserId,
                        Provider.FACEBOOK,
                        fbUserId,
                        longAccessToken,
                        TimeUtil.toUTCOffsetDateTime(expiresAt),
                        additionalOAuthInfo
                );
            }

            log.info("Facebook OAuth successfully completed for user: {}, Facebook ID: {}", managingUserId, fbUserId);
            return saved;

        } catch (Exception e) {
            log.error("Error during Facebook OAuth callback: {}", e.getMessage(), e);
            throw e;
        }
    }

    private Map<String, Object> exchangeShortLivedToken(String code) {
        String url = String.format(
            "https://graph.facebook.com/v21.0/oauth/access_token?client_id=%s&client_secret=%s&redirect_uri=%s&code=%s",
            appId, appSecret, redirectUri, code
        );

        log.debug("Short-lived token exchange URL: {}", url.replace(appSecret, "***"));

        try {
            Map<String, Object> response = rest.getForObject(url, Map.class);
            log.debug("Short-lived token response keys: {}", response.keySet());
            return response;
        } catch (Exception e) {
            log.error("Failed to exchange code for short-lived token: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange code for token: " + e.getMessage());
        }
    }

    private Map<String, Object> exchangeLongLivedToken(String shortToken) {
        String url = String.format(
            "https://graph.facebook.com/v21.0/oauth/access_token?grant_type=fb_exchange_token&client_id=%s&client_secret=%s&fb_exchange_token=%s",
            appId, appSecret, shortToken
        );

        log.debug("Long-lived token exchange URL: {}", url.replace(appSecret, "***"));

        try {
            Map<String, Object> response = rest.getForObject(url, Map.class);
            log.debug("Long-lived token response keys: {}", response.keySet());
            return response;
        } catch (Exception e) {
            log.error("Failed to exchange for long-lived token: {}", e.getMessage());
            throw new RuntimeException("Failed to get long-lived token: " + e.getMessage());
        }
    }

    private String fetchFacebookUserId(String accessToken) {
        String url = String.format(
            "https://graph.facebook.com/v21.0/me?fields=id&access_token=%s",
            accessToken
        );

        log.debug("Fetching Facebook user ID from: {}", url.replace(accessToken, "***"));

        try {
            Map response = rest.getForObject(url, Map.class);
            log.debug("Facebook user response: {}", response);
            return (String) response.get("id");
        } catch (Exception e) {
            log.error("Failed to fetch Facebook user ID: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch user info: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> fetchFacebookPages(String accessToken) {
        String url = String.format(
            "https://graph.facebook.com/v21.0/me/accounts?fields=id,name,access_token&access_token=%s",
            accessToken
        );

        log.debug("Fetching Facebook Pages from: {}", url.replace(accessToken, "***"));

        try {
            Map result = rest.getForObject(url, Map.class);
            List<Map<String, Object>> pages = (List<Map<String, Object>>) result.get("data");
            log.debug("Facebook Pages response: {} pages found", pages != null ? pages.size() : 0);
            return pages;
        } catch (Exception e) {
            log.warn("Failed to fetch Facebook Pages (non-critical): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Facebook long-lived tokens (~60 days) can be refreshed by exchanging the current
     * long-lived token for a new one via the fb_exchange_token grant.
     */
    public OAuthInfoEntity refreshAccessToken(OAuthInfoEntity info) {
        log.info("Refreshing Facebook long-lived token for OAuthInfo ID: {}", info.getId());

        Map<String, Object> response = exchangeLongLivedToken(info.getAccessToken());

        String newAccessToken = (String) response.get("access_token");
        Integer expiresIn = (Integer) response.get("expires_in");

        if (newAccessToken == null) {
            throw new RuntimeException("Facebook refresh did not return an access_token");
        }

        long newExpiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L;
        info.setAccessToken(newAccessToken);
        info.setExpiresAt(newExpiresAtMillis);
        info.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(newExpiresAtMillis));

        OAuthInfoEntity saved = repo.save(info);
        redisTokenExpirySaver.saveTokenExpiry(saved);

        log.info("Facebook token refreshed successfully for OAuthInfo ID: {}", info.getId());
        return saved;
    }

    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity info) {
        long now = System.currentTimeMillis();
        // Refresh if expiring within 24 hours
        if (info.getExpiresAt() - now > 24 * 60 * 60 * 1000L) {
            return info;
        }
        return refreshAccessToken(info);
    }
}
