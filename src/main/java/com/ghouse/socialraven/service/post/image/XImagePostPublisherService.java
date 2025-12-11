package com.ghouse.socialraven.service.post.image;

import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.service.provider.XOAuthService;
import com.ghouse.socialraven.service.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class XImagePostPublisherService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private XOAuthService xOAuthService;

    @Autowired
    private StorageService storageService;

    private static final String X_API_BASE = "https://api.twitter.com/2";
    private static final String X_UPLOAD_BASE = "https://upload.twitter.com/1.1";
    private static final int MAX_IMAGES = 4; // X allows max 4 images per tweet
    private static final long RETRY_DELAY_MS = 2000; // 2 seconds between retries
    private static final int MAX_RETRIES = 2; // Limited retries to avoid IP blocking

    public void postImagesToX(PostEntity post,
                              List<PostMediaEntity> mediaFiles,
                              OAuthInfoEntity authInfo) {
        try {
            // Step 0: Validate inputs
            if (mediaFiles == null || mediaFiles.isEmpty()) {
                throw new IllegalArgumentException("Cannot post to X without media files");
            }

            if (mediaFiles.size() > MAX_IMAGES) {
                log.warn("X supports max {} images, truncating from {}", MAX_IMAGES, mediaFiles.size());
                mediaFiles = mediaFiles.subList(0, MAX_IMAGES);
            }

            // Get valid OAuth token (OAuth 2.0)
            OAuthInfoEntity validOAuthInfo = xOAuthService.getValidOAuthInfo(authInfo);
            String accessToken = validOAuthInfo.getAccessToken();

            log.info("=== X Image Post Started ===");
            log.info("PostID: {}, MediaCount: {}", post.getId(), mediaFiles.size());

            List<String> mediaIds = new ArrayList<>();

            // Process each image with delay between uploads to avoid rate limiting
            for (int i = 0; i < mediaFiles.size(); i++) {
                PostMediaEntity media = mediaFiles.get(i);
                int currentIndex = i + 1;

                log.info("Processing image {}/{}: {}", currentIndex, mediaFiles.size(), media.getFileKey());

                try {
                    // Add delay between uploads (except for first image)
                    if (i > 0) {
                        Thread.sleep(1000); // 1 second delay between image uploads
                    }

                    // Step 1: Download image bytes from S3
                    byte[] imageBytes = storageService.downloadFileBytes(media.getFileKey());
                    log.debug("Downloaded {} bytes from S3 for: {}", imageBytes.length, media.getFileKey());

                    // Step 2: Upload image to X with retry logic
                    String mediaId = uploadImageToXWithRetry(imageBytes, accessToken, media.getFileKey());
                    log.info("Successfully uploaded image {}/{} to X - MediaID: {}",
                            currentIndex, mediaFiles.size(), mediaId);

                    // Step 3: Collect media ID
                    mediaIds.add(mediaId);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Upload interrupted", e);
                } catch (Exception e) {
                    log.error("Failed to upload image {}/{}: {} - Error: {}",
                            currentIndex, mediaFiles.size(), media.getFileKey(), e.getMessage());
                    // Don't throw immediately - try to post with whatever images we have
                    if (mediaIds.isEmpty()) {
                        throw new RuntimeException("Failed to upload any media to X", e);
                    }
                    log.warn("Continuing with {} successfully uploaded images", mediaIds.size());
                    break;
                }
            }

            // Step 4: Create tweet with uploaded images
            log.info("All {} images uploaded successfully. Creating tweet...", mediaIds.size());
            createTweetWithMedia(accessToken, post, mediaIds);

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

    // UPLOAD IMAGE WITH RETRY LOGIC
    private String uploadImageToXWithRetry(byte[] imageBytes, String accessToken, String fileKey) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                if (attempt > 0) {
                    log.warn("Retry attempt {} for {}", attempt + 1, fileKey);
                    Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                }

                return uploadImageToX(imageBytes, accessToken);

            } catch (HttpClientErrorException e) {
                lastException = e;
                int statusCode = e.getStatusCode().value();

                // Don't retry on certain errors
                if (statusCode == 401 || statusCode == 403) {
                    log.error("Authentication error, not retrying: {}", statusCode);
                    throw new RuntimeException("X authentication failed: " + statusCode, e);
                }

                if (statusCode == 413) {
                    log.error("Image too large, not retrying");
                    throw new RuntimeException("Image exceeds X size limit", e);
                }

                // Retry on rate limiting or server errors
                if (statusCode == 429 || statusCode >= 500) {
                    attempt++;
                    if (attempt < MAX_RETRIES) {
                        log.warn("Rate limited or server error ({}), will retry", statusCode);
                        continue;
                    }
                }

                throw new RuntimeException("X upload failed with status: " + statusCode, e);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Upload interrupted", e);
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    break;
                }
            }
        }

        throw new RuntimeException("Failed to upload image after " + MAX_RETRIES + " attempts", lastException);
    }

    // STEP 1: UPLOAD IMAGE TO X (Using v1.1 endpoint but OAuth 2.0 Bearer token)
    private String uploadImageToX(byte[] imageBytes, String accessToken) {
        // Note: Media upload still uses v1.1 endpoint even with OAuth 2.0
        // This is the current X API design - media upload hasn't migrated to v2 yet
        String url = X_UPLOAD_BASE + "/media/upload.json";

        try {
            if (imageBytes == null || imageBytes.length == 0) {
                throw new RuntimeException("Image bytes are empty or null");
            }

            // OAuth 2.0 Bearer token authentication
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("media", imageBytes);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            log.debug("Uploading {} bytes to X", imageBytes.length);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("X media upload failed with status: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            Object mediaIdObj = responseBody.get("media_id_string");

            if (mediaIdObj == null) {
                throw new RuntimeException("X response missing media_id_string");
            }

            String mediaId = mediaIdObj.toString();
            log.debug("Image uploaded successfully - MediaID: {}", mediaId);

            return mediaId;

        } catch (HttpClientErrorException e) {
            log.error("X API Error - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload image to X: {}", e.getMessage(), e);
            throw new RuntimeException("Image upload to X failed", e);
        }
    }

    // STEP 2: CREATE TWEET WITH IMAGES (X API v2)
    private void createTweetWithMedia(String accessToken,
                                      PostEntity post,
                                      List<String> mediaIds) {
        String url = X_API_BASE + "/tweets";

        if (mediaIds == null || mediaIds.isEmpty()) {
            throw new RuntimeException("Cannot create tweet without media IDs");
        }

        log.info("Creating tweet with {} images", mediaIds.size());

        // Build tweet text (X has 280 character limit)
        String tweetText = buildTweetText(post);

        // Build request body for X API v2
        Map<String, Object> body = new HashMap<>();
        body.put("text", tweetText);

        Map<String, Object> media = new HashMap<>();
        media.put("media_ids", mediaIds);
        body.put("media", media);

        // OAuth 2.0 Bearer token authentication
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            log.debug("Sending tweet creation request to X API v2");
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("X tweet creation failed with status: " + response.getStatusCode());
            }

            log.info("Tweet created successfully - Response: {}", response.getBody());

        } catch (HttpClientErrorException e) {
            log.error("X API Error - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("X tweet creation failed: " +
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to create tweet: {}", e.getMessage(), e);
            throw new RuntimeException("X tweet creation failed", e);
        }
    }

    // Helper method to build tweet text respecting X's character limit
    private String buildTweetText(PostEntity post) {
        StringBuilder text = new StringBuilder();

        // Add title if present
        if (post.getTitle() != null && !post.getTitle().trim().isEmpty()) {
            text.append(post.getTitle().trim());
        }

        // Add description if present
        if (post.getDescription() != null && !post.getDescription().trim().isEmpty()) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(post.getDescription().trim());
        }

        String result = text.toString();

        // Truncate to 280 characters if necessary
        if (result.length() > 280) {
            log.warn("Tweet text exceeds 280 characters, truncating from {} to 277", result.length());
            result = result.substring(0, 277) + "...";
        }

        // If still empty, provide default text
        if (result.trim().isEmpty()) {
            result = "Check out this post!";
        }

        return result;
    }
}