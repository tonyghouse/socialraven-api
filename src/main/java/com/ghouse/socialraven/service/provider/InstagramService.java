package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class InstagramService {

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

    public void handleCallback(String code, String userId) {

        // STEP 1: Exchange code → long-lived access token (direct exchange for Instagram Graph API)
        Map<String, Object> tokenResponse = exchangeForLongLivedToken(code);
        String longAccessToken = (String) tokenResponse.get("access_token");
        Integer expiresIn = (Integer) tokenResponse.get("expires_in");

        // STEP 2: Get Instagram Business Account ID and User ID
        Map<String, Object> igUserInfo = fetchInstagramUserId(longAccessToken);
        String instagramUserId = (String) igUserInfo.get("id");
        String username = (String) igUserInfo.get("username");

        // STEP 3: Check if already exists
        OAuthInfoEntity existingAuthInfo = repo.findByUserIdAndProviderAndProviderUserId(
                userId, Provider.INSTAGRAM, instagramUserId
        );
        if (existingAuthInfo != null) {
            throw new RuntimeException("Instagram account already connected");
        }

        // STEP 4: Save everything
        OAuthInfoEntity oAuthInfo = new OAuthInfoEntity();
        oAuthInfo.setProvider(Provider.INSTAGRAM);
        oAuthInfo.setUserId(userId);
        oAuthInfo.setAccessToken(longAccessToken);
        oAuthInfo.setExpiresAt(System.currentTimeMillis() + expiresIn * 1000L);
        oAuthInfo.setProviderUserId(instagramUserId);

        AdditionalOAuthInfo additional = new AdditionalOAuthInfo();
        additional.setInstagramBusinessId(instagramUserId);

        oAuthInfo.setAdditionalInfo(additional);
        repo.save(oAuthInfo);
    }

    private Map<String, Object> exchangeForLongLivedToken(String code) {
        String url = "https://api.instagram.com/oauth/access_token";

        // Instagram Graph API requires POST with form data
        String body = String.format(
                "client_id=%s&client_secret=%s&grant_type=authorization_code&redirect_uri=%s&code=%s",
                appId, appSecret, redirectUri, code
        );

        try {
            return rest.postForObject(url, body, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to exchange code for token: " + e.getMessage());
        }
    }

    private Map<String, Object> fetchInstagramUserId(String accessToken) {
        String url = String.format(
                "https://graph.instagram.com/me?fields=id,username,account_type&access_token=%s",
                accessToken
        );

        try {
            return rest.getForObject(url, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Instagram user info: " + e.getMessage());
        }
    }
}