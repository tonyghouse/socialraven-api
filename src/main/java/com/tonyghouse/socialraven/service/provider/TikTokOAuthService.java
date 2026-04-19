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

@Service
public class TikTokOAuthService {

    private static final Logger log = LoggerFactory.getLogger(TikTokOAuthService.class);
    private static final long REFRESH_WINDOW_MILLIS = 60L * 60L * 1000L;

    @Value("${tiktok.client.key}")
    private String clientKey;

    @Value("${tiktok.client.secret}")
    private String clientSecret;

    @Value("${tiktok.redirect.uri}")
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
        log.info("Starting TikTok OAuth callback for userId: {}", managingUserId);

        Map<String, Object> tokenResponse = exchangeCodeForTokens(code);
        String accessToken = requireString(tokenResponse.get("access_token"), "access_token");
        String refreshToken = requireString(tokenResponse.get("refresh_token"), "refresh_token");
        String openId = requireString(tokenResponse.get("open_id"), "open_id");
        Number expiresInValue = requireNumber(tokenResponse.get("expires_in"), "expires_in");
        long expiresAtMillis = System.currentTimeMillis() + expiresInValue.longValue() * 1000L;

        AdditionalOAuthInfo additionalOAuthInfo = new AdditionalOAuthInfo();
        additionalOAuthInfo.setTiktokRefreshToken(refreshToken);
        Object scopeValue = tokenResponse.get("scope");
        if (scopeValue != null) {
            additionalOAuthInfo.setTiktokScope(String.valueOf(scopeValue));
        }

        if (sessionId != null) {
            return oauthConnectionPersistenceService.saveClientConnection(
                    workspaceId,
                    managingUserId,
                    sessionId,
                    ownerDisplayName,
                    ownerEmail,
                    Provider.TIKTOK,
                    openId,
                    accessToken,
                    TimeUtil.toUTCOffsetDateTime(expiresAtMillis),
                    additionalOAuthInfo
            );
        }

        return oauthConnectionPersistenceService.saveWorkspaceMemberConnection(
                workspaceId,
                managingUserId,
                Provider.TIKTOK,
                openId,
                accessToken,
                TimeUtil.toUTCOffsetDateTime(expiresAtMillis),
                additionalOAuthInfo
        );
    }

    public OAuthInfoEntity refreshAccessToken(OAuthInfoEntity info) {
        AdditionalOAuthInfo additionalInfo = info.getAdditionalInfo();
        String refreshToken = additionalInfo != null ? additionalInfo.getTiktokRefreshToken() : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("TikTok refresh token is missing for OAuthInfo ID: " + info.getId());
        }

        log.info("Refreshing TikTok token for OAuthInfo ID: {}", info.getId());

        Map<String, Object> response = requestTokenBundle("refresh_token", refreshToken);
        String newAccessToken = requireString(response.get("access_token"), "access_token");
        String newRefreshToken = requireString(response.get("refresh_token"), "refresh_token");
        Number expiresInValue = requireNumber(response.get("expires_in"), "expires_in");
        long newExpiresAtMillis = System.currentTimeMillis() + expiresInValue.longValue() * 1000L;

        info.setAccessToken(newAccessToken);
        info.setExpiresAt(newExpiresAtMillis);
        info.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(newExpiresAtMillis));

        AdditionalOAuthInfo updatedInfo = info.getAdditionalInfo() != null ? info.getAdditionalInfo() : new AdditionalOAuthInfo();
        updatedInfo.setTiktokRefreshToken(newRefreshToken);
        Object scopeValue = response.get("scope");
        if (scopeValue != null) {
            updatedInfo.setTiktokScope(String.valueOf(scopeValue));
        }
        info.setAdditionalInfo(updatedInfo);

        OAuthInfoEntity saved = repo.save(info);
        redisTokenExpirySaver.saveTokenExpiry(saved);
        return saved;
    }

    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity info) {
        long now = System.currentTimeMillis();
        if (info.getExpiresAt() - now > REFRESH_WINDOW_MILLIS) {
            return info;
        }
        return refreshAccessToken(info);
    }

    private Map<String, Object> exchangeCodeForTokens(String code) {
        return requestTokenBundle("authorization_code", code);
    }

    private Map<String, Object> requestTokenBundle(String grantType, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_key", clientKey);
        formData.add("client_secret", clientSecret);
        formData.add("grant_type", grantType);
        if ("authorization_code".equals(grantType)) {
            formData.add("code", value);
            formData.add("redirect_uri", redirectUri);
        } else {
            formData.add("refresh_token", value);
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        Map<String, Object> response = rest.postForObject(
                "https://open.tiktokapis.com/v2/oauth/token/",
                request,
                Map.class
        );

        if (response == null) {
            throw new RuntimeException("TikTok token request returned an empty response");
        }

        Object error = response.get("error");
        if (error != null && !String.valueOf(error).isBlank()) {
            Object description = response.get("error_description");
            throw new RuntimeException("TikTok token request failed: " + error +
                    (description != null ? " - " + description : ""));
        }

        return response;
    }

    private String requireString(Object value, String fieldName) {
        if (value == null) {
            throw new RuntimeException("TikTok response missing " + fieldName);
        }

        String stringValue = String.valueOf(value).trim();
        if (stringValue.isEmpty()) {
            throw new RuntimeException("TikTok response missing " + fieldName);
        }
        return stringValue;
    }

    private Number requireNumber(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number;
        }
        throw new RuntimeException("TikTok response missing " + fieldName);
    }
}
