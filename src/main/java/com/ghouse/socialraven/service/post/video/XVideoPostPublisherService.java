package com.ghouse.socialraven.service.post.video;

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
public class XVideoPostPublisherService {

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

    // Twitter API endpoints
    private static final String MEDIA_UPLOAD_URL = "https://upload.twitter.com/1.1/media/upload.json";
    private static final String CREATE_TWEET_URL = "https://api.twitter.com/2/tweets";

    // Twitter video limits
    private static final long MAX_VIDEO_SIZE = 512 * 1024 * 1024L; // 512MB
    private static final int MAX_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB chunks

    public void postVideosToX(PostEntity post,
                              List<PostMediaEntity> mediaFiles,
                              OAuthInfoEntity authInfo, PostCollectionEntity postCollection) {
        try {
            // Step 0: Validate inputs
            if (mediaFiles == null || mediaFiles.isEmpty()) {
                throw new IllegalArgumentException("Cannot post to X without media files");
            }

            // Twitter allows only 1 video per tweet
            if (mediaFiles.size() > 1) {
                throw new IllegalArgumentException("X allows only 1 video per tweet. Received: " + mediaFiles.size());
            }

            // Get valid OAuth token and secret
            OAuthInfoEntity validOAuthInfo = xOAuthService.getValidOAuthInfo(authInfo);
            String accessToken = validOAuthInfo.getAccessToken();
            String tokenSecret = validOAuthInfo.getAdditionalInfo().getXTokenSecret();
            String xUserId = authInfo.getProviderUserId();

            if (tokenSecret == null || tokenSecret.isEmpty()) {
                throw new RuntimeException("X token secret is missing. User needs to reconnect.");
            }

            log.info("=== X Video Post Started ===");
            log.info("PostID: {}, XUserID: {}", post.getId(), xUserId);

            PostMediaEntity videoMedia = mediaFiles.get(0);
            log.info("Processing video: {}", videoMedia.getFileKey());

            // Step 1: Download video bytes from S3
            byte[] videoBytes = storageService.downloadFileBytes(videoMedia.getFileKey());
            log.info("Downloaded {} bytes from S3 for: {}", videoBytes.length, videoMedia.getFileKey());

            // Validate video size
            if (videoBytes.length > MAX_VIDEO_SIZE) {
                throw new RuntimeException("Video exceeds Twitter's 512MB limit: " + videoMedia.getFileKey());
            }

            // Step 2: Upload video using chunked upload
            String mediaId = uploadVideoToX(videoBytes, accessToken, tokenSecret, videoMedia);
            log.info("Successfully uploaded video to X - MediaID: {}", mediaId);

            // Step 3: Create tweet with video
            log.info("Creating tweet with video...");
            createTweetWithVideo(post, mediaId, accessToken, tokenSecret, postCollection);

            log.info("=== X Video Post Success ===");
            log.info("PostID: {}", post.getId());

        } catch (IllegalArgumentException e) {
            log.error("Invalid input for X video post - PostID: {} - Error: {}",
                    post.getId(), e.getMessage());
            throw e;
        } catch (Exception exp) {
            log.error("=== X Video Post Failed ===");
            log.error("PostID: {}, Error: {}", post.getId(), exp.getMessage(), exp);
            throw new RuntimeException("X Video Post Failed: " + post.getId(), exp);
        }
    }

    /**
     * Upload video to Twitter using chunked upload (required for videos)
     * Three-step process: INIT → APPEND → FINALIZE
     */
    private String uploadVideoToX(byte[] videoBytes, 
                                  String accessToken, 
                                  String tokenSecret,
                                  PostMediaEntity videoMedia) {
        try {
            // Determine media type
            String mediaType = determineMediaType(videoMedia.getFileKey());
            long totalBytes = videoBytes.length;

            log.info("Starting chunked video upload - Size: {} bytes, Type: {}", totalBytes, mediaType);

            // STEP 1: INIT - Initialize upload
            String mediaId = initVideoUpload(totalBytes, mediaType, accessToken, tokenSecret);
            log.info("Video upload initialized - MediaID: {}", mediaId);

            // STEP 2: APPEND - Upload video in chunks
            uploadVideoChunks(videoBytes, mediaId, accessToken, tokenSecret);
            log.info("All video chunks uploaded successfully");

            // STEP 3: FINALIZE - Finalize upload
            finalizeVideoUpload(mediaId, accessToken, tokenSecret);
            log.info("Video upload finalized");

            // STEP 4: Wait for processing (Twitter processes videos asynchronously)
            waitForVideoProcessing(mediaId, accessToken, tokenSecret);
            log.info("Video processing completed");

            return mediaId;

        } catch (Exception e) {
            log.error("Failed to upload video to X: {}", e.getMessage(), e);
            throw new RuntimeException("Video upload to X failed", e);
        }
    }

    /**
     * STEP 1: Initialize chunked upload
     */
    private String initVideoUpload(long totalBytes, 
                                   String mediaType, 
                                   String accessToken, 
                                   String tokenSecret) {
        try {
            // Build OAuth parameters
            Map<String, String> oauthParams = new LinkedHashMap<>();
            oauthParams.put("oauth_consumer_key", apiKey);
            oauthParams.put("oauth_token", accessToken);
            oauthParams.put("oauth_signature_method", "HMAC-SHA1");
            oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            oauthParams.put("oauth_nonce", generateNonce());
            oauthParams.put("oauth_version", "1.0");

            // Add request parameters
            Map<String, String> allParams = new LinkedHashMap<>(oauthParams);
            allParams.put("command", "INIT");
            allParams.put("total_bytes", String.valueOf(totalBytes));
            allParams.put("media_type", mediaType);
            allParams.put("media_category", "tweet_video");

            // Generate signature
            String signature = generateSignature("POST", MEDIA_UPLOAD_URL, allParams, tokenSecret);
            oauthParams.put("oauth_signature", signature);

            // Build Authorization header
            String authHeader = buildAuthHeader(oauthParams);

            // Build form body
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("command", "INIT");
            body.add("total_bytes", String.valueOf(totalBytes));
            body.add("media_type", mediaType);
            body.add("media_category", "tweet_video");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            // Send INIT request
            ResponseEntity<Map> response = restTemplate.exchange(
                MEDIA_UPLOAD_URL,
                HttpMethod.POST,
                request,
                Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Failed to initialize video upload");
            }

            Object mediaIdObj = response.getBody().get("media_id_string");
            if (mediaIdObj == null) {
                throw new RuntimeException("No media_id_string in response");
            }

            return mediaIdObj.toString();

        } catch (Exception e) {
            log.error("Failed to initialize video upload: {}", e.getMessage(), e);
            throw new RuntimeException("Video upload initialization failed", e);
        }
    }

    /**
     * STEP 2: Upload video in chunks
     */
    private void uploadVideoChunks(byte[] videoBytes, 
                                   String mediaId, 
                                   String accessToken, 
                                   String tokenSecret) {
        try {
            int segmentIndex = 0;
            int offset = 0;

            while (offset < videoBytes.length) {
                int chunkSize = Math.min(MAX_CHUNK_SIZE, videoBytes.length - offset);
                byte[] chunk = Arrays.copyOfRange(videoBytes, offset, offset + chunkSize);

                log.debug("Uploading chunk {}: {} bytes (offset: {})", segmentIndex, chunkSize, offset);

                uploadChunk(chunk, mediaId, segmentIndex, accessToken, tokenSecret);

                offset += chunkSize;
                segmentIndex++;

                // Progress logging
                int progress = (int) ((offset * 100.0) / videoBytes.length);
                log.info("Upload progress: {}% ({}/{} bytes)", progress, offset, videoBytes.length);
            }

        } catch (Exception e) {
            log.error("Failed to upload video chunks: {}", e.getMessage(), e);
            throw new RuntimeException("Video chunk upload failed", e);
        }
    }

    /**
     * Upload a single chunk
     */
    private void uploadChunk(byte[] chunk, 
                            String mediaId, 
                            int segmentIndex, 
                            String accessToken, 
                            String tokenSecret) {
        try {
            String base64Chunk = Base64.getEncoder().encodeToString(chunk);

            // Build OAuth parameters
            Map<String, String> oauthParams = new LinkedHashMap<>();
            oauthParams.put("oauth_consumer_key", apiKey);
            oauthParams.put("oauth_token", accessToken);
            oauthParams.put("oauth_signature_method", "HMAC-SHA1");
            oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            oauthParams.put("oauth_nonce", generateNonce());
            oauthParams.put("oauth_version", "1.0");

            // Add request parameters (including media for signature)
            Map<String, String> allParams = new LinkedHashMap<>(oauthParams);
            allParams.put("command", "APPEND");
            allParams.put("media_id", mediaId);
            allParams.put("segment_index", String.valueOf(segmentIndex));
            allParams.put("media", base64Chunk);

            // Generate signature
            String signature = generateSignature("POST", MEDIA_UPLOAD_URL, allParams, tokenSecret);
            oauthParams.put("oauth_signature", signature);

            // Build Authorization header
            String authHeader = buildAuthHeader(oauthParams);

            // Build form body
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("command", "APPEND");
            body.add("media_id", mediaId);
            body.add("segment_index", String.valueOf(segmentIndex));
            body.add("media", base64Chunk);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            // Send APPEND request
            ResponseEntity<String> response = restTemplate.exchange(
                MEDIA_UPLOAD_URL,
                HttpMethod.POST,
                request,
                String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to upload chunk " + segmentIndex);
            }

        } catch (Exception e) {
            log.error("Failed to upload chunk {}: {}", segmentIndex, e.getMessage(), e);
            throw new RuntimeException("Chunk upload failed", e);
        }
    }

    /**
     * STEP 3: Finalize upload
     */
    private void finalizeVideoUpload(String mediaId, 
                                     String accessToken, 
                                     String tokenSecret) {
        try {
            // Build OAuth parameters
            Map<String, String> oauthParams = new LinkedHashMap<>();
            oauthParams.put("oauth_consumer_key", apiKey);
            oauthParams.put("oauth_token", accessToken);
            oauthParams.put("oauth_signature_method", "HMAC-SHA1");
            oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            oauthParams.put("oauth_nonce", generateNonce());
            oauthParams.put("oauth_version", "1.0");

            // Add request parameters
            Map<String, String> allParams = new LinkedHashMap<>(oauthParams);
            allParams.put("command", "FINALIZE");
            allParams.put("media_id", mediaId);

            // Generate signature
            String signature = generateSignature("POST", MEDIA_UPLOAD_URL, allParams, tokenSecret);
            oauthParams.put("oauth_signature", signature);

            // Build Authorization header
            String authHeader = buildAuthHeader(oauthParams);

            // Build form body
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("command", "FINALIZE");
            body.add("media_id", mediaId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            // Send FINALIZE request
            ResponseEntity<Map> response = restTemplate.exchange(
                MEDIA_UPLOAD_URL,
                HttpMethod.POST,
                request,
                Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to finalize video upload");
            }

        } catch (Exception e) {
            log.error("Failed to finalize video upload: {}", e.getMessage(), e);
            throw new RuntimeException("Video finalization failed", e);
        }
    }

    /**
     * STEP 4: Wait for video processing
     * Twitter processes videos asynchronously, we need to poll for completion
     */
    private void waitForVideoProcessing(String mediaId, 
                                       String accessToken, 
                                       String tokenSecret) {
        try {
            int maxAttempts = 60; // 5 minutes max (5 seconds * 60)
            int attemptCount = 0;

            while (attemptCount < maxAttempts) {
                String status = checkVideoStatus(mediaId, accessToken, tokenSecret);

                if ("succeeded".equalsIgnoreCase(status)) {
                    log.info("Video processing succeeded");
                    return;
                } else if ("failed".equalsIgnoreCase(status)) {
                    throw new RuntimeException("Video processing failed");
                } else if ("in_progress".equalsIgnoreCase(status)) {
                    log.debug("Video processing in progress, waiting...");
                    Thread.sleep(5000); // Wait 5 seconds
                    attemptCount++;
                } else {
                    log.warn("Unknown video processing status: {}", status);
                    Thread.sleep(5000);
                    attemptCount++;
                }
            }

            throw new RuntimeException("Video processing timeout (5 minutes)");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Video processing interrupted", e);
        } catch (Exception e) {
            log.error("Failed to wait for video processing: {}", e.getMessage(), e);
            throw new RuntimeException("Video processing check failed", e);
        }
    }

    /**
     * Check video processing status
     */
    private String checkVideoStatus(String mediaId, 
                                    String accessToken, 
                                    String tokenSecret) {
        try {
            // Build OAuth parameters
            Map<String, String> oauthParams = new LinkedHashMap<>();
            oauthParams.put("oauth_consumer_key", apiKey);
            oauthParams.put("oauth_token", accessToken);
            oauthParams.put("oauth_signature_method", "HMAC-SHA1");
            oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            oauthParams.put("oauth_nonce", generateNonce());
            oauthParams.put("oauth_version", "1.0");

            // Add request parameters
            Map<String, String> allParams = new LinkedHashMap<>(oauthParams);
            allParams.put("command", "STATUS");
            allParams.put("media_id", mediaId);

            // Build URL with query parameters
            String url = MEDIA_UPLOAD_URL + "?command=STATUS&media_id=" + mediaId;

            // Generate signature
            String signature = generateSignature("GET", MEDIA_UPLOAD_URL, allParams, tokenSecret);
            oauthParams.put("oauth_signature", signature);

            // Build Authorization header
            String authHeader = buildAuthHeader(oauthParams);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            // Send STATUS request
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Failed to check video status");
            }

            Map<String, Object> processingInfo = (Map<String, Object>) response.getBody().get("processing_info");
            if (processingInfo != null && processingInfo.containsKey("state")) {
                return processingInfo.get("state").toString();
            }

            return "pending";

        } catch (Exception e) {
            log.error("Failed to check video status: {}", e.getMessage(), e);
            throw new RuntimeException("Video status check failed", e);
        }
    }

    /**
     * Create tweet with video using Twitter API v2
     */
    private void createTweetWithVideo(PostEntity post,
                                      String mediaId,
                                      String accessToken,
                                      String tokenSecret, PostCollectionEntity postCollection) {
        try {
            log.info("Creating tweet with video");

            // Build tweet text
            String tweetText = buildTweetText(post, postCollection);

            // Build request body for API v2
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", tweetText);
            
            // Add media
            Map<String, Object> media = new LinkedHashMap<>();
            media.put("media_ids", List.of(mediaId));
            body.put("media", media);

            String jsonBody = objectMapper.writeValueAsString(body);

            // Build OAuth parameters
            Map<String, String> oauthParams = new LinkedHashMap<>();
            oauthParams.put("oauth_consumer_key", apiKey);
            oauthParams.put("oauth_token", accessToken);
            oauthParams.put("oauth_signature_method", "HMAC-SHA1");
            oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            oauthParams.put("oauth_nonce", generateNonce());
            oauthParams.put("oauth_version", "1.0");

            // Generate signature
            String signature = generateSignature("POST", CREATE_TWEET_URL, oauthParams, tokenSecret);
            oauthParams.put("oauth_signature", signature);

            // Build Authorization header
            String authHeader = buildAuthHeader(oauthParams);

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

            log.info("Tweet with video created successfully - Response: {}", response.getBody());

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("X API Error during tweet creation - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("X tweet creation failed: " +
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to create tweet with video: {}", e.getMessage(), e);
            throw new RuntimeException("Tweet creation failed", e);
        }
    }

    /**
     * Determine media type from file extension
     */
    private String determineMediaType(String fileKey) {
        String lowerKey = fileKey.toLowerCase();
        if (lowerKey.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerKey.endsWith(".mov")) {
            return "video/quicktime";
        } else if (lowerKey.endsWith(".avi")) {
            return "video/x-msvideo";
        } else if (lowerKey.endsWith(".webm")) {
            return "video/webm";
        }
        // Default to mp4
        return "video/mp4";
    }

    /**
     * Build tweet text with character limit handling
     */
    private String buildTweetText(PostEntity post, PostCollectionEntity postCollection) {
        String text = postCollection.getDescription() != null ? postCollection.getDescription() : "";
        
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
            String paramString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));

            String signatureBase = method.toUpperCase() + "&" +
                urlEncode(url) + "&" +
                urlEncode(paramString);

            String signingKey = urlEncode(apiSecret) + "&" + urlEncode(tokenSecret);

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
     * Build Authorization header from OAuth parameters
     */
    private String buildAuthHeader(Map<String, String> oauthParams) {
        return "OAuth " + oauthParams.entrySet().stream()
            .map(e -> urlEncode(e.getKey()) + "=\"" + urlEncode(e.getValue()) + "\"")
            .collect(Collectors.joining(", "));
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