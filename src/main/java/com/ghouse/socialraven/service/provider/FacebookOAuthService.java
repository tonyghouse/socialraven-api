package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.util.TimeUtil;
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

    public void handleCallback(String code, String userId) {
        log.info("Starting Facebook OAuth callback for userId: {}", userId);
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

            // STEP 5: Check if already exists
            log.info("Step 5: Checking if Facebook account already connected");
            OAuthInfoEntity existingAuthInfo = repo.findByUserIdAndProviderAndProviderUserId(
                userId, Provider.FACEBOOK, fbUserId
            );
            if (existingAuthInfo != null) {
                log.warn("Facebook account {} already connected for user {}", fbUserId, userId);
                throw new RuntimeException("Facebook account already connected");
            }

            // STEP 6: Save everything
            log.info("Step 6: Saving OAuth info to database");
            OAuthInfoEntity oAuthInfo = new OAuthInfoEntity();
            oAuthInfo.setProvider(Provider.FACEBOOK);
            oAuthInfo.setUserId(userId);
            oAuthInfo.setAccessToken(longAccessToken);
            long expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
            oAuthInfo.setExpiresAt(expiresAt);
            oAuthInfo.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(expiresAt));
            oAuthInfo.setProviderUserId(fbUserId);

            AdditionalOAuthInfo additional = new AdditionalOAuthInfo();
            
//            // Store first page if available (you can modify to store all pages)
//            if (pages != null && !pages.isEmpty()) {
//                Map<String, Object> firstPage = pages.get(0);
//                additional.setFacebookPageId((String) firstPage.get("id"));
//                additional.setFacebookPageName((String) firstPage.get("name"));
//                additional.setFacebookPageAccessToken((String) firstPage.get("access_token"));
//                log.info("Stored Facebook Page: {} ({})", firstPage.get("name"), firstPage.get("id"));
//            }

            oAuthInfo.setAdditionalInfo(additional);

//            if (existingAuthInfo != null) {
//                oauthInfoEntity.setId(existingAuthInfo.getId());
//            }
            repo.save(oAuthInfo);

            log.info("Facebook OAuth successfully completed for user: {}, Facebook ID: {}", userId, fbUserId);

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
}