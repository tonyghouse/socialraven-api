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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder; // Added import
import redis.clients.jedis.JedisPool;

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
    private JedisPool jedisPool;

    public void handleCallback(String code, String userId) {
        log.info("Starting Instagram OAuth callback for userId: {}", userId);
        log.debug("Received code: {}", code);

        try {
            // STEP 1: Exchange code → short-lived token
            log.info("Step 1: Exchanging code for initial access token");
            Map<String, Object> tokenResponse = exchangeForAccessToken(code);
            String shortLivedAccessToken = (String) tokenResponse.get("access_token");
            Long instagramUserIdLong = ((Number) tokenResponse.get("user_id")).longValue();
            String instagramUserId = String.valueOf(instagramUserIdLong);

            // STEP 2: Exchange short-lived token for long-lived access token
            log.info("Step 2: Exchanging short-lived token for long-lived token");
            Map<String, Object> longLivedTokenResponse = exchangeForLongLivedToken(shortLivedAccessToken);

            try(var jedis = jedisPool.getResource()){
                jedis.set("instagram_token",longLivedTokenResponse.toString());
            }

            String longAccessToken = (String) longLivedTokenResponse.get("access_token");
            Long expiresIn = ((Number) longLivedTokenResponse.get("expires_in")).longValue();
            log.info("Successfully obtained long-lived access token. Expires in: {} seconds, Instagram User ID: {}",
                    expiresIn, instagramUserId);




            // STEP 4: Check if already exists
            log.info("Step 4: Checking if Instagram account already connected");
            OAuthInfoEntity existingAuthInfo = repo.findByUserIdAndProviderAndProviderUserId(
                    userId, Provider.INSTAGRAM, instagramUserId
            );
            if (existingAuthInfo != null) {
                log.warn("Instagram account {} already connected for user {}", instagramUserId, userId);
                throw new RuntimeException("Instagram account already connected");
            }
            OAuthInfoEntity oAuthInfo = new OAuthInfoEntity();
            oAuthInfo.setProvider(Provider.INSTAGRAM);
            oAuthInfo.setUserId(userId);
            oAuthInfo.setAccessToken(longAccessToken);
            var timeInMillis = System.currentTimeMillis() + expiresIn * 1000L;
            oAuthInfo.setExpiresAt(timeInMillis);
            oAuthInfo.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(timeInMillis));
            oAuthInfo.setProviderUserId(instagramUserId);

            AdditionalOAuthInfo additional = new AdditionalOAuthInfo();
            oAuthInfo.setAdditionalInfo(additional);
            repo.save(oAuthInfo);
            log.info("Instagram OAuth successfully completed for user: {}, Instagram ID: {}", userId, instagramUserId);

        } catch (Exception e) {
            log.error("Error during Instagram OAuth callback: {}", e.getMessage(), e);
            throw e;
        }
    }

    private Map<String, Object> exchangeForAccessToken(String code) {
        String url = "https://api.instagram.com/oauth/access_token";

        log.debug("Exchange token URL: {}", url);
        log.debug("Using appId: {}, redirectUri: {}", appId, redirectUri);

        try {
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
            /*
            SUCCESS Response structure:
              {
              access_token=GAAQQotZCvQmNBZA....,
              user_id=25223281824604,
              permissions=[instagram_business_basic]}
             */

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


    private Map<String, Object> exchangeForLongLivedToken(String shortAccessToken) {
        // Step 1: Set up the URL and parameters
//        String url = "https://graph.instagram.com/access_token";
        String url = "https://api.instagram.com/oauth/access_token";
        String clientSecret = appSecret;
        String accessToken = shortAccessToken;
        String grantType = "ig_exchange_token";

        // Step 2: Prepare the query parameters
        String params = "grant_type=" + grantType + "&client_secret=" + clientSecret + "&access_token=" + accessToken;

        // Step 3: Create the URL with query parameters
        String fullUrl = url + "?" + params;

        try {
            // Step 5: Make the GET request
            ResponseEntity<Map<String, Object>> response = rest.exchange(
                    fullUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            /*
               SUCCESS Response Structure:
                  {
                    "access_token": "new-token-here",
                    "token_type": "bearer",
                    "expires_in": 5183944
                   }
             */


            // Step 6: Print the response
            if (response.getStatusCode() == HttpStatus.OK) {
                System.out.println("Response Body: " + response.getBody());
                return response.getBody();
            }else{
                throw new RuntimeException("Failed to exchange for long-lived token");
            }
        } catch (Exception e) {
            log.error("Failed to exchange for long-lived token: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange for long-lived token: " + e.getMessage());
        }
    }


    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity info) {
        return info;
    }
}
