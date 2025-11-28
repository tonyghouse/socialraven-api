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
    // private long timeInMillis; // This variable was unused and is removed.

    public void handleCallback(String code, String userId) {
        log.info("Starting Instagram OAuth callback for userId: {}", userId);
        log.debug("Received code: {}", code);

        try {
            // STEP 1: Exchange code → short-lived token (The existing method already handles the immediate exchange correctly)
            log.info("Step 1: Exchanging code for initial access token");
            Map<String, Object> tokenResponse = exchangeForAccessToken(code);

            String accessToken = (String) tokenResponse.get("access_token");
            // The initial token response has "user_id" but not "expires_in" in the basic flow
            // We need to exchange again for the long-lived one if required immediately

            Long instagramUserIdLong = ((Number) tokenResponse.get("user_id")).longValue();
            String instagramUserId = String.valueOf(instagramUserIdLong);

            // STEP 2: Exchange short-lived token for long-lived access token
            log.info("Step 2: Exchanging short-lived token for long-lived token");
            Map<String, Object> longLivedTokenResponse = exchangeForLongLivedToken(accessToken);

            String longAccessToken = (String) longLivedTokenResponse.get("access_token");
            // Fixed the data type retrieval here
            Long expiresIn = ((Number) longLivedTokenResponse.get("expires_in")).longValue();

            log.info("Successfully obtained long-lived access token. Expires in: {} seconds, Instagram User ID: {}",
                    expiresIn, instagramUserId);

            // STEP 3: Get Instagram username (optional but nice to have)
            log.info("Step 3: Fetching Instagram username");
            String username = fetchInstagramUsername(longAccessToken, instagramUserId);
            log.info("Instagram username fetched: {}", username);

            // STEP 4: Check if already exists
            log.info("Step 4: Checking if Instagram account already connected");
            OAuthInfoEntity existingAuthInfo = repo.findByUserIdAndProviderAndProviderUserId(
                    userId, Provider.INSTAGRAM, instagramUserId
            );
            if (existingAuthInfo != null) {
                log.warn("Instagram account {} already connected for user {}", instagramUserId, userId);
                throw new RuntimeException("Instagram account already connected");
            }

            // STEP 5: Save everything
            log.info("Step 5: Saving OAuth info to database");
            log.info("====== TOKEN DEBUG INFO ======");
            log.info("Access Token Length: {}", longAccessToken.length());
            log.info("Access Token First 50 chars: {}", longAccessToken.substring(0, Math.min(50, longAccessToken.length())));
            log.info("Access Token Last 20 chars: {}", longAccessToken.substring(Math.max(0, longAccessToken.length() - 20)));
            log.info("Token contains spaces: {}", longAccessToken.contains(" "));
            log.info("Token contains quotes: {}", longAccessToken.contains("\""));
            log.info("Token starts with: {}", longAccessToken.substring(0, Math.min(10, longAccessToken.length())));
            log.info("==============================");

            OAuthInfoEntity oAuthInfo = new OAuthInfoEntity();
            oAuthInfo.setProvider(Provider.INSTAGRAM);
            oAuthInfo.setUserId(userId);
            oAuthInfo.setAccessToken(longAccessToken);
            var timeInMillis = System.currentTimeMillis() + expiresIn * 1000L;
            oAuthInfo.setExpiresAt(timeInMillis);
            oAuthInfo.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(timeInMillis));
            oAuthInfo.setProviderUserId(instagramUserId);

            AdditionalOAuthInfo additional = new AdditionalOAuthInfo();
            additional.setInstagramBusinessId(instagramUserId); // Assuming this holds IG User ID for now

            oAuthInfo.setAdditionalInfo(additional);
            repo.save(oAuthInfo);

            log.info("Instagram OAuth successfully completed for user: {}, Instagram ID: {}", userId, instagramUserId);

        } catch (Exception e) {
            log.error("Error during Instagram OAuth callback: {}", e.getMessage(), e);
            throw e;
        }
    }

    // Renamed this method to be more accurate (initial code exchange)
    private Map<String, Object> exchangeForAccessToken(String code) {
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

            if (response == null || !response.containsKey("access_token")) {
                throw new RuntimeException("Did not receive an access token in the response.");
            }

            log.info("Token exchange response keys: {}", response.keySet());
            log.debug("Token exchange full response: {}", response);
            return response;

        } catch (Exception e) {
            log.error("Failed to exchange code for token: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange code for token: " + e.getMessage());
        }
    }

    // Created a separate method for exchanging for a long-lived token from a short-lived one
    private Map<String, Object> exchangeForLongLivedToken(String shortAccessToken) {
        // Use the Graph API endpoint for exchanging for a long-lived token
        String url = String.format("graph.instagram.com",
                appSecret, shortAccessToken);

        log.info("Sending GET request to exchange for a long-lived token");
        try {
            // The exchange for a long-lived token uses a GET request
            Map<String, Object> response = rest.getForObject(url, Map.class);

            if (response == null || !response.containsKey("access_token")) {
                throw new RuntimeException("Did not receive a long-lived access token in the response.");
            }

            return response;
        } catch (Exception e) {
            log.error("Failed to exchange for long-lived token: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange for long-lived token: " + e.getMessage());
        }
    }


    private String fetchInstagramUsername(String accessToken, String userId) {
        // Use the correct Instagram Graph API endpoint with GET for user details
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

        // 1. If token still valid (> 24 hours remaining) → return it
        // Refresh 24 hours before expiry to be safe
        if (info.getExpiresAt() - now > 24 * 60 * 60 * 1000L) {
            return info;
        }

        // 2. Expired or near expiry → refresh
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
                // Fixed the logic flow and error handling here
                throw new RuntimeException("Token refresh failed: No token in response.");
            }

            // Update entity with new token info
            info.setAccessToken((String) response.get("access_token"));
            Long expiresIn = ((Number) response.get("expires_in")).longValue(); // Fixed data type
            long timeInMillis = System.currentTimeMillis() + expiresIn * 1000L;
            info.setExpiresAt(timeInMillis);
            info.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(timeInMillis));

            // Save the refreshed info back to the database
            repo.save(info);
            log.info("Token refresh successful for IG User {}", info.getProviderUserId());
            return info;

        } catch (Exception e) {
            log.error("Failed to refresh token for IG User {}: {}", info.getProviderUserId(), e.getMessage());
            throw new RuntimeException("Failed to refresh Instagram access token: " + e.getMessage());
        }
    }
}
