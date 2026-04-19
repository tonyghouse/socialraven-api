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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ThreadsOAuthService {

    private static final Logger log = LoggerFactory.getLogger(ThreadsOAuthService.class);

    @Value("${threads.app.id}")
    private String appId;

    @Value("${threads.app.secret}")
    private String appSecret;

    @Value("${threads.redirect.uri}")
    private String redirectUri;

    @Autowired
    private OAuthInfoRepo repo;

    @Autowired
    private RestTemplate rest;

    @Autowired
    private RedisTokenExpirySaver redisTokenExpirySaver;

    @Autowired
    private OAuthConnectionPersistenceService oauthConnectionPersistenceService;

    public void handleCallback(String code, String userId) {
        handleCallback(code, userId, WorkspaceContext.getWorkspaceId(), null, null, null);
    }

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
        log.info("Starting Threads OAuth callback for userId: {}", managingUserId);

        Map<String, Object> shortTokenResponse = exchangeCodeForShortLivedToken(code);
        String shortLivedAccessToken = requireString(shortTokenResponse.get("access_token"), "short-lived access token");
        String providerUserId = requireString(shortTokenResponse.get("user_id"), "Threads user id");

        Map<String, Object> longTokenResponse = exchangeForLongLivedToken(shortLivedAccessToken);
        String longLivedAccessToken = requireString(longTokenResponse.get("access_token"), "long-lived access token");
        Number expiresInValue = requireNumber(longTokenResponse.get("expires_in"), "expires_in");
        long expiresAtMillis = System.currentTimeMillis() + expiresInValue.longValue() * 1000L;

        AdditionalOAuthInfo additionalOAuthInfo = new AdditionalOAuthInfo();
        if (sessionId != null) {
            return oauthConnectionPersistenceService.saveClientConnection(
                    workspaceId,
                    managingUserId,
                    sessionId,
                    ownerDisplayName,
                    ownerEmail,
                    Provider.THREADS,
                    providerUserId,
                    longLivedAccessToken,
                    TimeUtil.toUTCOffsetDateTime(expiresAtMillis),
                    additionalOAuthInfo
            );
        }

        return oauthConnectionPersistenceService.saveWorkspaceMemberConnection(
                workspaceId,
                managingUserId,
                Provider.THREADS,
                providerUserId,
                longLivedAccessToken,
                TimeUtil.toUTCOffsetDateTime(expiresAtMillis),
                additionalOAuthInfo
        );
    }

    public OAuthInfoEntity refreshAccessToken(OAuthInfoEntity info) {
        log.info("Refreshing Threads long-lived token for OAuthInfo ID: {}", info.getId());

        String url = UriComponentsBuilder
                .fromHttpUrl("https://graph.threads.net/refresh_access_token")
                .queryParam("grant_type", "th_refresh_token")
                .queryParam("access_token", info.getAccessToken())
                .toUriString();

        Map<String, Object> response = rest.getForObject(url, Map.class);
        String newAccessToken = requireString(response != null ? response.get("access_token") : null, "refreshed access token");
        Number expiresInValue = requireNumber(response != null ? response.get("expires_in") : null, "expires_in");
        long newExpiresAtMillis = System.currentTimeMillis() + expiresInValue.longValue() * 1000L;

        info.setAccessToken(newAccessToken);
        info.setExpiresAt(newExpiresAtMillis);
        info.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(newExpiresAtMillis));

        OAuthInfoEntity saved = repo.save(info);
        redisTokenExpirySaver.saveTokenExpiry(saved);
        return saved;
    }

    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity info) {
        long now = System.currentTimeMillis();
        if (info.getExpiresAt() - now > 24 * 60 * 60 * 1000L) {
            return info;
        }
        return refreshAccessToken(info);
    }

    private Map<String, Object> exchangeCodeForShortLivedToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", appId);
        formData.add("client_secret", appSecret);
        formData.add("grant_type", "authorization_code");
        formData.add("redirect_uri", redirectUri);
        formData.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        Map<String, Object> response = rest.postForObject(
                "https://graph.threads.net/oauth/access_token",
                request,
                Map.class
        );

        if (response == null) {
            throw new RuntimeException("Threads short-lived token exchange returned an empty response");
        }

        return response;
    }

    private Map<String, Object> exchangeForLongLivedToken(String shortLivedAccessToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://graph.threads.net/access_token")
                .queryParam("grant_type", "th_exchange_token")
                .queryParam("client_secret", appSecret)
                .queryParam("access_token", shortLivedAccessToken)
                .toUriString();

        Map<String, Object> response = rest.getForObject(url, Map.class);
        if (response == null) {
            throw new RuntimeException("Threads long-lived token exchange returned an empty response");
        }

        return response;
    }

    private String requireString(Object value, String fieldName) {
        if (value == null) {
            throw new RuntimeException("Threads response missing " + fieldName);
        }

        String stringValue = String.valueOf(value).trim();
        if (stringValue.isEmpty()) {
            throw new RuntimeException("Threads response missing " + fieldName);
        }
        return stringValue;
    }

    private Number requireNumber(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number;
        }
        throw new RuntimeException("Threads response missing " + fieldName);
    }
}
