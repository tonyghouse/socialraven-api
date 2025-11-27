package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
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

import java.util.Map;

@Service
public class InstagramService {

    private static final Logger log = LoggerFactory.getLogger(InstagramService.class);

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
        log.info("Starting Instagram OAuth callback for userId: {}", userId);
        log.debug("Received code: {}", code);

        try {
            // STEP 1: Exchange code → long-lived access token
            log.info("Step 1: Exchanging code for long-lived token");
            Map<String, Object> tokenResponse = exchangeForLongLivedToken(code);
            String longAccessToken = (String) tokenResponse.get("access_token");
            Integer expiresIn = (Integer) tokenResponse.get("expires_in");
            log.info("Successfully obtained access token. Expires in: {} seconds", expiresIn);

            // STEP 2: Get Instagram Business Account ID and User ID
            log.info("Step 2: Fetching Instagram user info");
            Map<String, Object> igUserInfo = fetchInstagramUserId(longAccessToken);
            String instagramUserId = (String) igUserInfo.get("id");
            String username = (String) igUserInfo.get("username");
            log.info("Instagram user fetched - ID: {}, Username: {}", instagramUserId, username);

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
            oAuthInfo.setExpiresAt(System.currentTimeMillis() + expiresIn * 1000L);
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

            log.debug("Token exchange response: {}", response);
            return response;

        } catch (Exception e) {
            log.error("Failed to exchange code for token: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange code for token: " + e.getMessage());
        }
    }

    private Map<String, Object> fetchInstagramUserId(String accessToken) {
        String url = String.format(
                "https://graph.instagram.com/me?fields=id,username,account_type&access_token=%s",
                accessToken
        );

        log.debug("Fetching Instagram user info from: {}", url.replace(accessToken, "***"));

        try {
            Map<String, Object> response = rest.getForObject(url, Map.class);
            log.debug("Instagram user info response: {}", response);
            return response;

        } catch (Exception e) {
            log.error("Failed to fetch Instagram user info: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch Instagram user info: " + e.getMessage());
        }
    }
}