package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.helper.RedisTokenExpirySaver;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.util.SecurityContextUtil;

import java.util.HashMap;
import java.util.Map;

import com.ghouse.socialraven.util.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


@Service
public class YouTubeOAuthService {

    @Value("${youtube.client.id}")
    private String clientId;

    @Value("${youtube.client.secret}")
    private String clientSecret;

    @Value("${youtube.redirect.uri}")
    private String redirectUri;

    @Autowired
    private OAuthInfoRepo repo;

    @Autowired
    private RedisTokenExpirySaver redisTokenExpirySaver;

    public void exchangeCodeForTokens(String code) {
        RestTemplate rest = new RestTemplate();

        // STEP 1: Exchange authorization code for tokens
        String tokenUrl = "https://oauth2.googleapis.com/token";

        Map<String, String> params = new HashMap<>();
        params.put("code", code);
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        params.put("redirect_uri", redirectUri);
        params.put("grant_type", "authorization_code");

        ResponseEntity<Map> resp = rest.postForEntity(tokenUrl, params, Map.class);

        String accessToken = (String) resp.getBody().get("access_token");
        String refreshToken = (String) resp.getBody().get("refresh_token");
        Integer expiresIn = (Integer) resp.getBody().get("expires_in");

        // STEP 2: Fetch YouTube Provider User ID (CHANNEL ID)
        String providerUserId = fetchYoutubeChannelId(accessToken);

        // STEP 3: Save in DB
        OAuthInfoEntity oauthInfoEntity = new OAuthInfoEntity();
        oauthInfoEntity.setProvider(Provider.YOUTUBE);
        oauthInfoEntity.setProviderUserId(providerUserId);
        oauthInfoEntity.setAccessToken(accessToken);
        long expiresAtMillis = System.currentTimeMillis() + (expiresIn * 1000L);
        oauthInfoEntity.setExpiresAt(expiresAtMillis);
        oauthInfoEntity.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(expiresAtMillis));

        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        oauthInfoEntity.setUserId(userId);

        AdditionalOAuthInfo additionalOAuthInfo = new AdditionalOAuthInfo();
        additionalOAuthInfo.setYoutubeRefreshToken(refreshToken);
        oauthInfoEntity.setAdditionalInfo(additionalOAuthInfo);

        OAuthInfoEntity existingAuthInfo = repo.findByUserIdAndProviderAndProviderUserId(userId, Provider.YOUTUBE, oauthInfoEntity.getProviderUserId());
        if (existingAuthInfo != null) {
            oauthInfoEntity.setId(existingAuthInfo.getId());
        }


        repo.save(oauthInfoEntity);
        redisTokenExpirySaver.saveTokenExpiry(oauthInfoEntity);
    }

    // =====================
    // FETCH YOUTUBE CHANNEL ID
    // =====================
    private String fetchYoutubeChannelId(String accessToken) {
        RestTemplate rest = new RestTemplate();

        String url = "https://www.googleapis.com/youtube/v3/channels?part=id&mine=true";

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        org.springframework.http.HttpEntity<String> entity =
                new org.springframework.http.HttpEntity<>(headers);

        ResponseEntity<Map> response =
                rest.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);

        // Extract the first channel id
        var items = (java.util.List<Map<String, Object>>) response.getBody().get("items");
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("No YouTube channel found for the authenticated user");
        }

        Map<String, Object> firstItem = items.get(0);
        return (String) firstItem.get("id"); // The channel ID ✔
    }

    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity info) {

        long now = System.currentTimeMillis();

        // Token still valid → return it
        if (info.getExpiresAt() - now > 24 * 60 * 60 * 1000L) {
            return info;
        }

        // Otherwise refresh token
        return refreshAccessToken(info);
    }

    public OAuthInfoEntity refreshAccessToken(OAuthInfoEntity info) {

        String refreshToken = info.getAdditionalInfo().getYoutubeRefreshToken();
        if (refreshToken == null) {
            throw new RuntimeException("No YouTube refresh token stored");
        }

        RestTemplate rest = new RestTemplate();
        String tokenUrl = "https://oauth2.googleapis.com/token";

        Map<String, String> params = new HashMap<>();
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        params.put("refresh_token", refreshToken);
        params.put("grant_type", "refresh_token");

        ResponseEntity<Map> resp = rest.postForEntity(tokenUrl, params, Map.class);

        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to refresh YouTube token: " + resp);
        }

        Map body = resp.getBody();
        String newAccessToken = (String) body.get("access_token");
        Integer expiresIn = (Integer) body.get("expires_in");

        if (newAccessToken == null) {
            throw new RuntimeException("YouTube did not return access_token");
        }

        long newExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;

        info.setAccessToken(newAccessToken);
        info.setExpiresAt(newExpiresAt);
        info.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(newExpiresAt));

        // Usually Google DOES NOT return a new refresh token here (only on first auth)
        // But save if present
        if (body.containsKey("refresh_token")) {
            info.getAdditionalInfo().setYoutubeRefreshToken((String) body.get("refresh_token"));
        }

        return repo.save(info);
    }



}

