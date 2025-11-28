package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;

import java.util.Map;

import com.ghouse.socialraven.util.TimeUtil;
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
    private long timeInMillis;

    public void handleCallback(String code, String userId) {
        log.info("Starting Instagram OAuth callback for userId: {}", userId);
        log.debug("Received code: {}", code);

        try {
            // STEP 1: Exchange code → long-lived access token
            log.info("Step 1: Exchanging code for long-lived token");
            Map<String, Object> tokenResponse = exchangeForLongLivedToken(code);

            String longAccessToken = (String) tokenResponse.get("access_token");
            Long expiresIn = tokenResponse.get("expires_in") != null
                    ? ((Number) tokenResponse.get("expires_in")).longValue()
                    : 5183944L; // Default ~60 days for IG long-lived tokens

            // Instagram User ID comes in the token response!
            Long instagramUserIdLong = ((Number) tokenResponse.get("user_id")).longValue();
            String instagramUserId = String.valueOf(instagramUserIdLong);

            log.info("Successfully obtained access token. Expires in: {} seconds, Instagram User ID: {}",
                    expiresIn, instagramUserId);

            // STEP 2: Get Instagram username (optional but nice to have)
            log.info("Step 2: Fetching Instagram username");
            String username = fetchInstagramUsername(longAccessToken, instagramUserId);
            log.info("Instagram username fetched: {}", username);

            // STEP 3: Check if already exists
            log.info("Step 3: Checking if Instagram account already connected");
            OAuthInfoEntity existingAuthInfo = repo.findByUserIdAndProviderAndProviderUserId(
                    userId, Provider.INSTAGRAM, instagramUserId
            );
            if (existingAuthInfo != null) {
                log.warn("Instagram account {} already connected for user {}", instagramUserId, userId);
                throw new RuntimeException("Instagram account already connected");
            }

            // STEP 4: Save everything
            log.info("Step 4: Saving OAuth info to database");
            OAuthInfoEntity oAuthInfo = new OAuthInfoEntity();
            oAuthInfo.setProvider(Provider.INSTAGRAM);
            oAuthInfo.setUserId(userId);
            oAuthInfo.setAccessToken(longAccessToken);
            var timeInMillis = System.currentTimeMillis() + expiresIn * 1000L;
            oAuthInfo.setExpiresAt(timeInMillis);
            oAuthInfo.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(timeInMillis));
            oAuthInfo.setProviderUserId(instagramUserId);

            AdditionalOAuthInfo additional = new AdditionalOAuthInfo();
            additional.setInstagramBusinessId(instagramUserId);

            oAuthInfo.setAdditionalInfo(additional);
            repo.save(oAuthInfo);

            log.info("Instagram OAuth successfully completed for user: {}, Instagram ID: {}", userId, instagramUserId);

        } catch (Exception e) {
            log.error("Error during Instagram OAuth callback: {}", e.getMessage(), e);
            throw e;
        }
    }

    private Map<String, Object> exchangeForLongLivedToken(String code) {
        String url = "https://api.instagram.com/oauth/access_token";

        log.debug("Exchange token URL: {}", url);
        log.debug("Using appId: {}, redirectUri: {}", appId, redirectUri);

        try {
            // Prepare form data
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("client_id", appId);
            formData.add("client_secret", appSecret);
            formData.add("grant_type", "authorization_code");
            formData.add("redirect_uri", redirectUri);
            formData.add("code", code);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

            log.info("Sending POST request to exchange code for token");
            Map<String, Object> response = rest.postForObject(url, request, Map.class);

            log.info("Token exchange response keys: {}", response.keySet());
            log.debug("Token exchange full response: {}", response);
            return response;

        } catch (Exception e) {
            log.error("Failed to exchange code for token: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange code for token: " + e.getMessage());
        }
    }

    private String fetchInstagramUsername(String accessToken, String userId) {
        // Use the correct Instagram Graph API endpoint with POST
        String url = String.format(
                "https://graph.instagram.com/%s?fields=username&access_token=%s",
                userId, accessToken
        );

        log.debug("Fetching Instagram username from: {}", url.replace(accessToken, "***"));

        try {
            Map<String, Object> response = rest.getForObject(url, Map.class);
            log.debug("Instagram username response: {}", response);
            return (String) response.get("username");

        } catch (Exception e) {
            log.warn("Failed to fetch Instagram username (non-critical): {}", e.getMessage());
            return "unknown"; // Fallback if username fetch fails
        }
    }


    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity info) {

        long now = System.currentTimeMillis();

        // 1. If token still valid → return it
        if (info.getExpiresAt() - now > 24 * 60 * 60 * 1000L) {
            return info;
        }

        // 2. Expired → refresh
        return refreshAccessToken(info);
    }

    private OAuthInfoEntity refreshAccessToken(OAuthInfoEntity info) {
        try {
            String url = "https://graph.instagram.com/refresh_access_token" +
                    "?grant_type=ig_refresh_token" +
                    "&access_token=" + info.getAccessToken();

            log.info("Refreshing Instagram long-lived token for IG User {}", info.getProviderUserId());

            Map<String, Object> response = rest.getForObject(url, Map.class);

            if (response == null || response.get("access_token") == null) {
                throw new RuntimeException("Invalid refresh response from Instagram.");
            }

            // Extract new token + expiry
            String newAccessToken = (String) response.get("access_token");
            Long expiresIn = ((Number) response.get("expires_in")).longValue(); // seconds

            long newExpiryMillis = System.currentTimeMillis() + (expiresIn * 1000L);

            // Update entity
            info.setAccessToken(newAccessToken);
            info.setExpiresAt(newExpiryMillis);
            info.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(newExpiryMillis));

            repo.save(info);

            log.info("Instagram token refreshed successfully. New expiry: {}", newExpiryMillis);

            return info;

        } catch (Exception e) {
            log.error("Failed to refresh Instagram token: {}", e.getMessage());
            throw new RuntimeException("Instagram token refresh failed: " + e.getMessage());
        }
    }

}