package com.ghouse.socialraven.service.account_profile;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class XProfileService {

    private final RestTemplate rest = new RestTemplate();

    @Value("${x.api.key}")
    private String consumerKey;

    @Value("${x.api.secret}")
    private String consumerSecret;

    private static final String VERIFY_CREDENTIALS_URL = "https://api.twitter.com/1.1/account/verify_credentials.json";

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {
        try {
            // Validate token secret exists
            if (info.getAdditionalInfo() == null ||
                    info.getAdditionalInfo().getXTokenSecret() == null) {
                log.error("X token secret is missing for user: {}", info.getProviderUserId());
                return null;
            }

            String accessToken = info.getAccessToken();
            String tokenSecret = info.getAdditionalInfo().getXTokenSecret();

            log.debug("Fetching X profile for user: {}", info.getProviderUserId());

            // Generate OAuth 1.0a parameters
            Map<String, String> oauthParams = generateOAuthParams(accessToken);

            // Generate signature
            String signature = generateSignature(
                    HttpMethod.GET.name(),
                    VERIFY_CREDENTIALS_URL,
                    oauthParams,
                    Collections.emptyMap(),
                    tokenSecret
            );
            oauthParams.put("oauth_signature", signature);

            // Build Authorization header
            String authHeader = buildAuthorizationHeader(oauthParams);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Make API call
            ResponseEntity<Map> response = rest.exchange(
                    VERIFY_CREDENTIALS_URL,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to fetch X profile - Status: {}", response.getStatusCode());
                return null;
            }

            Map<String, Object> data = response.getBody();

            // Build ConnectedAccount DTO
            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(info.getProviderUserId());
            dto.setPlatform(Platform.x);
            dto.setUsername((String) data.get("screen_name")); // Twitter username (e.g., @username)

            // Get profile image URL (replace _normal with _bigger for better quality)
            String profileImageUrl = (String) data.get("profile_image_url_https");
            if (profileImageUrl != null) {
                profileImageUrl = profileImageUrl.replace("_normal", "_bigger");
            }
            dto.setProfilePicLink(profileImageUrl);

            log.info("Successfully fetched X profile for @{}", data.get("screen_name"));
            return dto;

        } catch (Exception exp) {
            log.error("X Profile fetching Failed: {}", exp.getMessage(), exp);
            return null;
        }
    }

    // ========== OAUTH 1.0A HELPER METHODS ==========

    private Map<String, String> generateOAuthParams(String accessToken) {
        Map<String, String> params = new HashMap<>();
        params.put("oauth_consumer_key", consumerKey);
        params.put("oauth_token", accessToken);
        params.put("oauth_signature_method", "HMAC-SHA1");
        params.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("oauth_nonce", generateNonce());
        params.put("oauth_version", "1.0");
        return params;
    }

    private String generateNonce() {
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce)
                .replaceAll("\\W", "")
                .substring(0, 32);
    }

    private String generateSignature(String httpMethod,
                                     String url,
                                     Map<String, String> oauthParams,
                                     Map<String, String> queryParams,
                                     String tokenSecret) {
        try {
            // Combine OAuth parameters (exclude oauth_signature) and query parameters
            Map<String, String> allParams = new TreeMap<>();
            for (Map.Entry<String, String> entry : oauthParams.entrySet()) {
                if (!"oauth_signature".equals(entry.getKey())) {
                    allParams.put(entry.getKey(), entry.getValue());
                }
            }
            allParams.putAll(queryParams);

            // Build parameter string (sorted, percent-encoded)
            String paramString = allParams.entrySet().stream()
                    .map(e -> percentEncode(e.getKey()) + "=" + percentEncode(e.getValue()))
                    .collect(Collectors.joining("&"));

            // Build signature base string
            String signatureBaseString = httpMethod.toUpperCase() + "&" +
                    percentEncode(url) + "&" +
                    percentEncode(paramString);

            // Build signing key
            String signingKey = percentEncode(consumerSecret) + "&" + percentEncode(tokenSecret);

            // Generate HMAC-SHA1 signature
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(
                    signingKey.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA1"
            );
            mac.init(keySpec);
            byte[] signatureBytes = mac.doFinal(
                    signatureBaseString.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder().encodeToString(signatureBytes);

        } catch (Exception e) {
            log.error("Failed to generate OAuth signature: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate OAuth signature", e);
        }
    }

    private String buildAuthorizationHeader(Map<String, String> oauthParams) {
        return "OAuth " + oauthParams.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> percentEncode(e.getKey()) + "=\"" + percentEncode(e.getValue()) + "\"")
                .collect(Collectors.joining(", "));
    }

    private String percentEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            throw new RuntimeException("Failed to percent-encode value: " + value, e);
        }
    }
}