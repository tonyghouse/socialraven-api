package com.ghouse.socialraven.service.post.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostCollectionEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.service.provider.XOAuthService;
import com.ghouse.socialraven.service.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
public class XImagePostPublisherService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private XOAuthService xOAuthService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${x.api.key}")
    private String apiKey;

    @Value("${x.api.secret}")
    private String apiSecret;

    // Twitter API v1.1 endpoints (OAuth 1.0a requires v1.1 for media upload)
    private static final String MEDIA_UPLOAD_URL = "https://upload.twitter.com/1.1/media/upload.json";
    
    // Twitter API v2 endpoint for creating tweets
    private static final String CREATE_TWEET_URL = "https://api.twitter.com/2/tweets";

    public void postImagesToX(PostEntity post,
                              List<PostMediaEntity> mediaFiles,
                              OAuthInfoEntity authInfo, PostCollectionEntity postCollectionEntity) {
        try {
            // Step 0: Validate inputs
            if (mediaFiles == null || mediaFiles.isEmpty()) {
                throw new IllegalArgumentException("Cannot post to X without media files");
            }

            // Validate media count (Twitter allows max 4 images)
            if (mediaFiles.size() > 4) {
                throw new IllegalArgumentException("X allows maximum 4 images per tweet. Received: " + mediaFiles.size());
            }

            // Get valid OAuth token and secret
            OAuthInfoEntity validOAuthInfo = xOAuthService.getValidOAuthInfo(authInfo);
            String accessToken = validOAuthInfo.getAccessToken();
            String tokenSecret = validOAuthInfo.getAdditionalInfo().getXTokenSecret();
            String xUserId = authInfo.getProviderUserId();

            if (tokenSecret == null || tokenSecret.isEmpty()) {
                throw new RuntimeException("X token secret is missing. User needs to reconnect.");
            }

            log.info("=== X Image Post Started ===");
            log.info("PostID: {}, MediaCount: {}, XUserID: {}",
                    post.getId(), mediaFiles.size(), xUserId);

            List<String> mediaIds = new ArrayList<>();

            // Process each image
            for (int i = 0; i < mediaFiles.size(); i++) {
                PostMediaEntity media = mediaFiles.get(i);
                int currentIndex = i + 1;

                log.info("Processing image {}/{}: {}", currentIndex, mediaFiles.size(), media.getFileKey());

                try {
                    // Step 1: Download image bytes from S3
                    byte[] imageBytes = storageService.downloadFileBytes(media.getFileKey());
                    log.debug("Downloaded {} bytes from S3 for: {}", imageBytes.length, media.getFileKey());

                    // Validate image size (Twitter max: 5MB for images)
                    if (imageBytes.length > 5 * 1024 * 1024) {
                        throw new RuntimeException("Image exceeds Twitter's 5MB limit: " + media.getFileKey());
                    }

                    // Step 2: Upload image to Twitter
                    String mediaId = uploadImageToX(imageBytes, accessToken, tokenSecret);
                    log.info("Successfully uploaded image {}/{} to X - MediaID: {}", 
                            currentIndex, mediaFiles.size(), mediaId);

                    // Step 3: Collect media IDs
                    mediaIds.add(mediaId);

                } catch (Exception e) {
                    log.error("Failed to upload image {}/{}: {} - Error: {}",
                            currentIndex, mediaFiles.size(), media.getFileKey(), e.getMessage(), e);
                    throw new RuntimeException("Failed to upload media: " + media.getFileKey(), e);
                }
            }

            // Step 4: Create tweet with all uploaded images
            log.info("All {} images uploaded successfully. Creating tweet...", mediaIds.size());
            createTweetWithMedia(post, mediaIds, accessToken, tokenSecret, postCollectionEntity);

            log.info("=== X Image Post Success ===");
            log.info("PostID: {}, ImagesPosted: {}", post.getId(), mediaIds.size());

        } catch (IllegalArgumentException e) {
            log.error("Invalid input for X post - PostID: {} - Error: {}",
                    post.getId(), e.getMessage());
            throw e;
        } catch (Exception exp) {
            log.error("=== X Image Post Failed ===");
            log.error("PostID: {}, Error: {}", post.getId(), exp.getMessage(), exp);
            throw new RuntimeException("X Image(s) Post Failed: " + post.getId(), exp);
        }
    }

    /**
     * STEP 1: Upload image to Twitter using OAuth 1.0a
     * Uses Twitter API v1.1 media/upload endpoint
     */
    private String uploadImageToX(byte[] imageBytes, String accessToken, String tokenSecret) {
        try {
            log.debug("Uploading {} bytes to X", imageBytes.length);

            // Encode image as base64
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // Build OAuth parameters
            Map<String, String> oauthParams = new LinkedHashMap<>();
            oauthParams.put("oauth_consumer_key", apiKey);
            oauthParams.put("oauth_token", accessToken);
            oauthParams.put("oauth_signature_method", "HMAC-SHA1");
            oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            oauthParams.put("oauth_nonce", generateNonce());
            oauthParams.put("oauth_version", "1.0");

            // For media upload, we need to include the media parameter in signature
            Map<String, String> allParams = new LinkedHashMap<>(oauthParams);
            allParams.put("media", base64Image);

            // Generate signature
            String signature = generateSignature("POST", MEDIA_UPLOAD_URL, allParams, tokenSecret);
            oauthParams.put("oauth_signature", signature);

            // Build Authorization header
            String authHeader = "OAuth " + oauthParams.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=\"" + urlEncode(e.getValue()) + "\"")
                .collect(Collectors.joining(", "));

            // Build form body
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("media", base64Image);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            // Upload media
            ResponseEntity<Map> response = restTemplate.exchange(
                MEDIA_UPLOAD_URL, 
                HttpMethod.POST, 
                request, 
                Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("X media upload failed with status: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("media_id_string")) {
                throw new RuntimeException("X media upload response missing media_id_string");
            }

            String mediaId = responseBody.get("media_id_string").toString();
            log.debug("Image uploaded successfully to X - MediaID: {}", mediaId);

            return mediaId;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("X API Error during media upload - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("X media upload failed: " + 
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to upload image to X: {}", e.getMessage(), e);
            throw new RuntimeException("Image upload to X failed", e);
        }
    }

    /**
     * STEP 2: Create tweet with media using Twitter API v2
     * Note: Twitter API v2 also uses OAuth 1.0a for authentication
     */
    private void createTweetWithMedia(PostEntity post,
                                      List<String> mediaIds,
                                      String accessToken,
                                      String tokenSecret, PostCollectionEntity postCollectionEntity) {
        try {
            if (mediaIds == null || mediaIds.isEmpty()) {
                throw new RuntimeException("Cannot create tweet without media IDs");
            }

            log.info("Creating tweet with {} images", mediaIds.size());

            // Build tweet text (Twitter has 280 character limit)
            String tweetText = buildTweetText(post, postCollectionEntity);

            // Build request body for API v2
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", tweetText);
            
            // Add media
            Map<String, Object> media = new LinkedHashMap<>();
            media.put("media_ids", mediaIds);
            body.put("media", media);

            String jsonBody = objectMapper.writeValueAsString(body);

            // Build OAuth parameters (no body parameters for signature in v2)
            Map<String, String> oauthParams = new LinkedHashMap<>();
            oauthParams.put("oauth_consumer_key", apiKey);
            oauthParams.put("oauth_token", accessToken);
            oauthParams.put("oauth_signature_method", "HMAC-SHA1");
            oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            oauthParams.put("oauth_nonce", generateNonce());
            oauthParams.put("oauth_version", "1.0");

            // Generate signature (API v2 doesn't include body in signature)
            String signature = generateSignature("POST", CREATE_TWEET_URL, oauthParams, tokenSecret);
            oauthParams.put("oauth_signature", signature);

            // Build Authorization header
            String authHeader = "OAuth " + oauthParams.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=\"" + urlEncode(e.getValue()) + "\"")
                .collect(Collectors.joining(", "));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            // Create tweet
            log.debug("Sending tweet creation request to X");
            ResponseEntity<Map> response = restTemplate.exchange(
                CREATE_TWEET_URL, 
                HttpMethod.POST, 
                request, 
                Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("X tweet creation failed with status: " + response.getStatusCode());
            }

            log.info("Tweet created successfully - Response: {}", response.getBody());

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("X API Error during tweet creation - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("X tweet creation failed: " + 
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to create tweet: {}", e.getMessage(), e);
            throw new RuntimeException("Tweet creation failed", e);
        }
    }

    /**
     * Build tweet text with character limit handling
     */
    private String buildTweetText(PostEntity post, PostCollectionEntity postCollection) {
        String text = postCollection.getDescription() != null ? postCollection.getDescription() : "";
        
        // Twitter character limit is 280
        if (text.length() > 280) {
            log.warn("Tweet text exceeds 280 characters, truncating...");
            text = text.substring(0, 277) + "...";
        }
        
        return text;
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
            String signingKey = urlEncode(apiSecret) + "&" + urlEncode(tokenSecret);

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
}