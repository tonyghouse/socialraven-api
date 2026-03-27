package com.tonyghouse.socialraven.service.provider;

import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.helper.RedisTokenExpirySaver;
import com.tonyghouse.socialraven.model.AdditionalOAuthInfo;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.util.TimeUtil;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
public class InstagramOAuthService {

    private static final Logger log = LoggerFactory.getLogger(InstagramOAuthService.class);

    @Value("${instagram.app.id}")
    private String appId;

    @Value("${instagram.app.secret}")
    private String appSecret;

    @Value("${instagram.redirect.uri}")
    private String redirectUri;

    @Autowired
    private OAuthInfoRepo repo;

    @Autowired
    private RestTemplate rest;

    @Autowired
    private RedisTokenExpirySaver redisTokenExpirySaver;

    public void handleCallback(String code, String userId) {
        log.info("Starting Instagram OAuth callback for userId: {}", userId);

        // STEP 1: Exchange code → access token
        // The new Instagram Platform API (IGAA tokens) returns a long-lived token (60 days)
        // directly from this exchange. The separate ig_exchange_token step is only for the
        // old Basic Display API (IGQVJ tokens) and the EAA token flow.
        Map<String, Object> tokenResponse = exchangeForAccessToken(code);
        String accessToken = (String) tokenResponse.get("access_token");
        String instagramUserId = String.valueOf(((Number) tokenResponse.get("user_id")).longValue());
        log.info("Obtained access token for Instagram user ID: {}", instagramUserId);

        // IGAA tokens are already long-lived (60 days). The ig_exchange_token endpoint
        // is only for EAA/IGQVJ token formats.
        long expiresIn = 60L * 24 * 60 * 60; // 60 days in seconds

        // STEP 2: Upsert — update if already connected, otherwise insert
        OAuthInfoEntity oAuthInfo = repo.findByUserIdAndProviderAndProviderUserId(
                userId, Provider.INSTAGRAM, instagramUserId
        );
        if (oAuthInfo == null) {
            oAuthInfo = new OAuthInfoEntity();
            oAuthInfo.setProvider(Provider.INSTAGRAM);
            oAuthInfo.setUserId(userId);
            oAuthInfo.setProviderUserId(instagramUserId);
            oAuthInfo.setWorkspaceId(WorkspaceContext.getWorkspaceId());
            log.info("Creating new Instagram OAuth record for user: {}", userId);
        } else {
            log.info("Updating existing Instagram OAuth record for user: {}", userId);
        }

        long expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L;
        oAuthInfo.setAccessToken(accessToken);
        oAuthInfo.setExpiresAt(expiresAtMillis);
        oAuthInfo.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(expiresAtMillis));

        if (oAuthInfo.getAdditionalInfo() == null) {
            oAuthInfo.setAdditionalInfo(new AdditionalOAuthInfo());
        }

        OAuthInfoEntity saved = repo.save(oAuthInfo);
        redisTokenExpirySaver.saveTokenExpiry(saved);

        log.info("Instagram OAuth successfully completed for user: {}, Instagram ID: {}", userId, instagramUserId);
    }

    /**
     * Instagram long-lived tokens are refreshed by calling the refresh endpoint
     * with the current valid long-lived token. No separate refresh_token needed.
     */
    public OAuthInfoEntity refreshAccessToken(OAuthInfoEntity info) {
        log.info("Refreshing Instagram long-lived token for OAuthInfo ID: {}", info.getId());

        String url = UriComponentsBuilder
                .fromHttpUrl("https://graph.instagram.com/refresh_access_token")
                .queryParam("grant_type", "ig_refresh_token")
                .queryParam("access_token", info.getAccessToken())
                .toUriString();

        Map<String, Object> response = rest.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        ).getBody();

        if (response == null || !response.containsKey("access_token")) {
            throw new RuntimeException("Instagram refresh did not return an access_token");
        }

        String newAccessToken = (String) response.get("access_token");
        Long expiresIn = ((Number) response.get("expires_in")).longValue();
        long newExpiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L;

        info.setAccessToken(newAccessToken);
        info.setExpiresAt(newExpiresAtMillis);
        info.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(newExpiresAtMillis));

        OAuthInfoEntity saved = repo.save(info);
        redisTokenExpirySaver.saveTokenExpiry(saved);

        log.info("Instagram token refreshed successfully for OAuthInfo ID: {}", info.getId());
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

    private Map<String, Object> exchangeForAccessToken(String code) {
        String url = "https://api.instagram.com/oauth/access_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", appId);
        formData.add("client_secret", appSecret);
        formData.add("grant_type", "authorization_code");
        formData.add("redirect_uri", redirectUri);
        formData.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        Map<String, Object> response = rest.postForObject(url, request, Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new RuntimeException("Instagram did not return an access_token in the short-lived token exchange");
        }

        return response;
    }

}
