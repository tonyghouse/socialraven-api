package com.ghouse.socialraven.service.post.text;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.repo.PostRepo;
import com.ghouse.socialraven.service.provider.XOAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
public class XTextPostPublisherService {

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private RestTemplate rest;

    @Autowired
    private OAuthInfoRepo oAuthInfoRepo;

    @Autowired
    private XOAuthService xOAuthService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${x.api.key}")
    private String apiKey;

    @Value("${x.api.secret}")
    private String apiSecret;

    // Twitter API v2 endpoint for creating tweets
    private static final String CREATE_TWEET_URL = "https://api.twitter.com/2/tweets";

    public void postTextToX(PostEntity post, OAuthInfoEntity authInfo) {
        try {
            // Get valid OAuth info with token secret
            OAuthInfoEntity validOAuthInfo = xOAuthService.getValidOAuthInfo(authInfo);
            String accessToken = validOAuthInfo.getAccessToken();
            String tokenSecret = validOAuthInfo.getAdditionalInfo().getXTokenSecret();

            if (tokenSecret == null || tokenSecret.isEmpty()) {
                throw new RuntimeException("X token secret is missing. User needs to reconnect.");
            }


            // Build tweet text with character limit handling
            String tweetText = buildTweetText(post);

            // Build request body for Twitter API v2
            Map<String, Object> body = new HashMap<>();
            body.put("text", tweetText);

            String jsonBody = objectMapper.writeValueAsString(body);

            // Build OAuth 1.0a parameters (no body parameters in signature for v2)
            Map<String, String> oauthParams = new LinkedHashMap<>();
            oauthParams.put("oauth_consumer_key", apiKey);
            oauthParams.put("oauth_token", accessToken);
            oauthParams.put("oauth_signature_method", "HMAC-SHA1");
            oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            oauthParams.put("oauth_nonce", generateNonce());
            oauthParams.put("oauth_version", "1.0");

            // Generate OAuth 1.0a signature
            String signature = generateSignature("POST", CREATE_TWEET_URL, oauthParams, tokenSecret);
            oauthParams.put("oauth_signature", signature);

            // Build Authorization header
            String authHeader = "OAuth " + oauthParams.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=\"" + urlEncode(e.getValue()) + "\"")
                .collect(Collectors.joining(", "));

            // Build HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentLength(jsonBody.getBytes(StandardCharsets.UTF_8).length);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            // Send request to Twitter
            log.debug("Sending tweet creation request to X");
            ResponseEntity<String> response = rest.exchange(
                CREATE_TWEET_URL,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("X tweet creation failed with status: " + response.getStatusCode());
            }

            log.info("X Text Post Success → {}", response.getBody());

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("X API Error - Status: {}, Body: {}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("X Text Post Failed: " + post.getId() + 
                " - " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception exp) {
            log.error("X Text Post Failed → {}", exp.getMessage(), exp);
            throw new RuntimeException("X Text Post Failed: " + post.getId(), exp);
        }
    }

    /**
     * Build tweet text with character limit handling
     * Twitter has a 280 character limit
     */
    private String buildTweetText(PostEntity post) {
        String text = post.getDescription() != null ? post.getDescription() : "";
        
        // Twitter character limit is 280
        if (text.length() > 280) {
            log.warn("Tweet text exceeds 280 characters ({}), truncating...", text.length());
            text = text.substring(0, 277) + "...";
        }
        
        if (text.isEmpty()) {
            log.warn("Tweet text is empty, using default message");
            text = "Posted via SocialRaven";
        }
        
        return text;
    }

    /**
     * Generate OAuth 1.0a signature using HMAC-SHA1
     */
    private String generateSignature(String method, String url, Map<String, String> params, String tokenSecret) {
        try {
            // Sort parameters alphabetically
            String paramString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));

            // Create signature base string
            // Format: HTTP_METHOD&URL_ENCODED_BASE_URL&URL_ENCODED_PARAMETERS
            String signatureBase = method.toUpperCase() + "&" + 
                urlEncode(url) + "&" + 
                urlEncode(paramString);

            // Create signing key
            // Format: CONSUMER_SECRET&TOKEN_SECRET
            String signingKey = urlEncode(apiSecret) + "&" + urlEncode(tokenSecret);

            // Generate HMAC-SHA1 signature
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec secret = new SecretKeySpec(
                signingKey.getBytes(StandardCharsets.UTF_8), 
                "HmacSHA1"
            );
            mac.init(secret);
            byte[] digest = mac.doFinal(signatureBase.getBytes(StandardCharsets.UTF_8));

            // Base64 encode the signature
            return Base64.getEncoder().encodeToString(digest);

        } catch (Exception e) {
            log.error("Failed to generate OAuth signature: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate OAuth signature", e);
        }
    }

    /**
     * Generate cryptographically secure random nonce
     * Used to prevent replay attacks
     */
    private String generateNonce() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder()
            .encodeToString(bytes)
            .replaceAll("[^A-Za-z0-9]", "");
    }

    /**
     * URL encode according to OAuth 1.0a spec (RFC 3986)
     * Encodes all characters except: A-Z, a-z, 0-9, -, ., _, ~
     */
    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
                .replace("+", "%20")    // Space should be %20, not +
                .replace("*", "%2A")     // * should be encoded
                .replace("%7E", "~");    // ~ should NOT be encoded (per RFC 3986)
        } catch (Exception e) {
            throw new RuntimeException("URL encoding failed", e);
        }
    }
}