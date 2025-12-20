// com.ghouse.socialraven.service.oauth.XOAuthService
package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.dto.XOAuthCallbackRequest;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.helper.RedisTokenExpirySaver;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.util.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class XOAuthService {

    @Value("${x.api.key}") // Consumer Key (OAuth 1.0a)
    private String apiKey;

    @Value("${x.api.secret}") // Consumer Secret (OAuth 1.0a)
    private String apiSecret;

    @Value("${x.callback.uri}")
    private String callbackUri;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private OAuthInfoRepo oAuthInfoRepo;

    @Autowired
    private RedisTokenExpirySaver redisTokenExpirySaver;

    // OAuth 1.0a URLs
    private static final String REQUEST_TOKEN_URL = "https://api.twitter.com/oauth/request_token";
    private static final String ACCESS_TOKEN_URL = "https://api.twitter.com/oauth/access_token";
    private static final String VERIFY_CREDENTIALS_URL = "https://api.twitter.com/1.1/account/verify_credentials.json";

    /**
     * Handle OAuth 1.0a callback from frontend
     * Frontend sends: accessToken, accessTokenSecret, userId, screenName
     */
    public void handleCallback(XOAuthCallbackRequest req) {
        log.info("X OAuth 1.0a callback for user: {}", req.getScreenName());

        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());

        // Verify the tokens by making an API call
        try {
            Map<String, Object> userInfo = verifyCredentials(req.getAccessToken(), req.getAccessTokenSecret());

            String providerUserId = userInfo.get("id_str").toString();
            String screenName = (String) userInfo.get("screen_name");

            // OAuth 1.0a tokens don't expire, so set a far future date
            OffsetDateTime expiresAtUtc = OffsetDateTime.now(ZoneOffset.UTC).plusYears(100);
            long expiresAtMillis = expiresAtUtc.toInstant().toEpochMilli();

            // Check if user already connected this X account
            OAuthInfoEntity existingAuthInfo = oAuthInfoRepo.findByUserIdAndProviderAndProviderUserId(
                    userId, Provider.X, providerUserId
            );

            OAuthInfoEntity info;
            if (existingAuthInfo != null) {
                log.info("Updating existing X OAuth connection for user: {}", screenName);
                info = existingAuthInfo;
            } else {
                log.info("Creating new X OAuth connection for user: {}", screenName);
                info = new OAuthInfoEntity();
                info.setProvider(Provider.X);
                info.setProviderUserId(providerUserId);
                info.setUserId(userId);
            }

            // Update token info
            info.setAccessToken(req.getAccessToken());
            info.setExpiresAt(expiresAtMillis);
            info.setExpiresAtUtc(expiresAtUtc);

            // Store token secret in additionalInfo (REQUIRED for non-null constraint)
            AdditionalOAuthInfo additionalInfo = info.getAdditionalInfo();
            if (additionalInfo == null) {
                additionalInfo = new AdditionalOAuthInfo();
                info.setAdditionalInfo(additionalInfo); // Set it immediately to avoid null
            }
            additionalInfo.setXTokenSecret(req.getAccessTokenSecret());

            oAuthInfoRepo.save(info);
            redisTokenExpirySaver.saveTokenExpiry(info);

            log.info("Successfully saved X OAuth 1.0a tokens for @{}", screenName);

        } catch (Exception e) {
            log.error("Failed to verify X credentials: {}", e.getMessage(), e);
            throw new RuntimeException("Invalid X OAuth tokens: " + e.getMessage(), e);
        }
    }

    /**
     * Verify credentials using OAuth 1.0a
     */
    private Map<String, Object> verifyCredentials(String accessToken, String tokenSecret) {
        try {
            String baseUrl = VERIFY_CREDENTIALS_URL;

            Map<String, String> oauthParams = new LinkedHashMap<>();
            oauthParams.put("oauth_consumer_key", apiKey);
            oauthParams.put("oauth_token", accessToken);
            oauthParams.put("oauth_signature_method", "HMAC-SHA1");
            oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            oauthParams.put("oauth_nonce", generateNonce());
            oauthParams.put("oauth_version", "1.0");

            // Add query parameters to signature calculation
            Map<String, String> allParams = new LinkedHashMap<>(oauthParams);
            allParams.put("include_email", "true");

            // Generate signature with ALL parameters (OAuth + query params)
            String signature = generateSignature("GET", baseUrl, allParams, tokenSecret);
            oauthParams.put("oauth_signature", signature);

            // Build Authorization header (only OAuth params, NOT query params)
            String authHeader = "OAuth " + oauthParams.entrySet().stream()
                    .map(e -> urlEncode(e.getKey()) + "=\"" + urlEncode(e.getValue()) + "\"")
                    .collect(Collectors.joining(", "));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);

            // Make request with query parameter in URL
            String requestUrl = baseUrl + "?include_email=true";
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    requestUrl, HttpMethod.GET, entity, Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to verify credentials: " + response.getStatusCode());
            }

            return response.getBody();

        } catch (Exception e) {
            log.error("Error verifying X credentials: {}", e.getMessage());
            log.error("Complete Error - Error verifying X credentials ", e);
            throw new RuntimeException("Failed to verify X credentials", e);
        }
    }


    /**
     * Get valid OAuth info (OAuth 1.0a tokens don't expire)
     */
    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity authInfo) {
        // OAuth 1.0a tokens don't expire, but verify they're still valid
        try {
            String tokenSecret = authInfo.getAdditionalInfo().getXTokenSecret();
            if (tokenSecret == null) {
                throw new RuntimeException("Missing X token secret. User needs to reconnect.");
            }

            // Optionally verify the token is still valid
            verifyCredentials(authInfo.getAccessToken(), tokenSecret);

            return authInfo;

        } catch (Exception e) {
            log.error("X OAuth token validation failed: {}", e.getMessage());
            throw new RuntimeException("X token is invalid. User needs to reconnect their X account.", e);
        }
    }

    /**
     * Generate OAuth 1.0a signature
     */
    private String generateSignature(String method, String url, Map<String, String> params, String tokenSecret) {
        try {
            // Sort parameters
            String paramString = params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                    .collect(Collectors.joining("&"));

            // Create signature base string
            String signatureBase = method.toUpperCase() + "&" +
                    urlEncode(url) + "&" +
                    urlEncode(paramString);

            // Create signing key
            String signingKey = urlEncode(apiSecret) + "&" + urlEncode(tokenSecret != null ? tokenSecret : "");

            // Generate HMAC-SHA1 signature
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec secret = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(secret);
            byte[] digest = mac.doFinal(signatureBase.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(digest);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate OAuth signature", e);
        }
    }

    /**
     * Generate random nonce
     */
    private String generateNonce() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes).replaceAll("[^A-Za-z0-9]", "");
    }

    /**
     * URL encode helper
     */
    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            throw new RuntimeException("URL encoding failed", e);
        }
    }

    /**
     * Make authenticated API call using OAuth 1.0a
     * Use this method when you need to call Twitter API with user's credentials
     */
    public <T> ResponseEntity<T> makeAuthenticatedRequest(
            String url,
            HttpMethod method,
            OAuthInfoEntity authInfo,
            Class<T> responseType) {

        String tokenSecret = authInfo.getAdditionalInfo().getXTokenSecret();
        if (tokenSecret == null) {
            throw new RuntimeException("Missing X token secret");
        }

        Map<String, String> oauthParams = new LinkedHashMap<>();
        oauthParams.put("oauth_consumer_key", apiKey);
        oauthParams.put("oauth_token", authInfo.getAccessToken());
        oauthParams.put("oauth_signature_method", "HMAC-SHA1");
        oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        oauthParams.put("oauth_nonce", generateNonce());
        oauthParams.put("oauth_version", "1.0");

        // Generate signature
        String signature = generateSignature(method.name(), url, oauthParams, tokenSecret);
        oauthParams.put("oauth_signature", signature);

        // Build Authorization header
        String authHeader = "OAuth " + oauthParams.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=\"" + urlEncode(e.getValue()) + "\"")
                .collect(Collectors.joining(", "));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, method, entity, responseType);
    }
}