package com.ghouse.socialraven.service.post.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.service.provider.LinkedInOAuthService;
import com.ghouse.socialraven.service.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LinkedInVideoPostPublisherService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LinkedInOAuthService linkedInOAuthService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private ObjectMapper objectMapper;

    // LinkedIn API endpoints
    private static final String VIDEO_INIT_URL = "https://api.linkedin.com/rest/videos?action=initializeUpload";
    private static final String VIDEO_STATUS_URL = "https://api.linkedin.com/rest/videos/";
    private static final String POST_CREATE_URL = "https://api.linkedin.com/rest/posts";
    
    // LinkedIn video limits
    private static final long MAX_VIDEO_SIZE = 5L * 1024 * 1024 * 1024; // 5GB
    private static final long MIN_VIDEO_SIZE = 75 * 1024; // 75KB

    public void postVideosToLinkedIn(PostEntity post,
                                     List<PostMediaEntity> mediaFiles,
                                     OAuthInfoEntity authInfo) {
        try {
            // Step 0: Validate inputs
            if (mediaFiles == null || mediaFiles.isEmpty()) {
                throw new IllegalArgumentException("Cannot post to LinkedIn without media files");
            }

            // LinkedIn allows only 1 video per post
            if (mediaFiles.size() > 1) {
                throw new IllegalArgumentException("LinkedIn allows only 1 video per post. Received: " + mediaFiles.size());
            }

            // Get valid OAuth token
            OAuthInfoEntity validOAuthInfo = linkedInOAuthService.getValidOAuthInfo(authInfo);
            String accessToken = validOAuthInfo.getAccessToken();
            String linkedInUserId = authInfo.getProviderUserId();

            log.info("=== LinkedIn Video Post Started ===");
            log.info("PostID: {}, LinkedInUserID: {}", post.getId(), linkedInUserId);

            PostMediaEntity videoMedia = mediaFiles.get(0);
            log.info("Processing video: {}", videoMedia.getFileKey());

            // Step 1: Download video bytes from S3
            byte[] videoBytes = storageService.downloadFileBytes(videoMedia.getFileKey());
            log.info("Downloaded {} bytes from S3 for: {}", videoBytes.length, videoMedia.getFileKey());

            // Validate video size
            if (videoBytes.length < MIN_VIDEO_SIZE) {
                throw new RuntimeException("Video is too small (< 75KB): " + videoMedia.getFileKey());
            }
            if (videoBytes.length > MAX_VIDEO_SIZE) {
                throw new RuntimeException("Video exceeds LinkedIn's 5GB limit: " + videoMedia.getFileKey());
            }

            // Step 2: Initialize video upload and get upload URL
            String videoUrn = initializeVideoUpload(videoBytes.length, accessToken, linkedInUserId);
            log.info("Video upload initialized - VideoURN: {}", videoUrn);

            // Step 3: Upload video to LinkedIn
            uploadVideoToLinkedIn(videoBytes, videoUrn, accessToken);
            log.info("Video uploaded successfully");

            // Step 4: Wait for video processing
            waitForVideoProcessing(videoUrn, accessToken);
            log.info("Video processing completed");

            // Step 5: Create post with video
            log.info("Creating LinkedIn post with video...");
            createPostWithVideo(post, videoUrn, accessToken, linkedInUserId);

            log.info("=== LinkedIn Video Post Success ===");
            log.info("PostID: {}", post.getId());

        } catch (IllegalArgumentException e) {
            log.error("Invalid input for LinkedIn video post - PostID: {} - Error: {}",
                    post.getId(), e.getMessage());
            throw e;
        } catch (Exception exp) {
            log.error("=== LinkedIn Video Post Failed ===");
            log.error("PostID: {}, Error: {}", post.getId(), exp.getMessage(), exp);
            throw new RuntimeException("LinkedIn Video Post Failed: " + post.getId(), exp);
        }
    }

    /**
     * STEP 1: Initialize video upload
     * Returns upload URL and video URN
     */
    private String initializeVideoUpload(long fileSizeBytes, String accessToken, String linkedInUserId) {
        try {
            String url = VIDEO_INIT_URL;

            // Build request body
            Map<String, Object> body = Map.of(
                "initializeUploadRequest", Map.of(
                    "owner", "urn:li:person:" + linkedInUserId,
                    "fileSizeBytes", fileSizeBytes,
                    "uploadCaptions", false,
                    "uploadThumbnail", false
                )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("LinkedIn-Version", "202511");
            headers.set("X-RestLi-Protocol-Version", "2.0.0");

            HttpEntity<Object> request = new HttpEntity<>(body, headers);

            log.debug("Initializing video upload for {} bytes", fileSizeBytes);
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Failed to initialize video upload");
            }

            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> value = (Map<String, Object>) responseBody.get("value");

            if (value == null) {
                throw new RuntimeException("No 'value' in response");
            }

            String videoUrn = (String) value.get("video");
            String uploadUrl = (String) value.get("uploadUrl");

            if (videoUrn == null || uploadUrl == null) {
                throw new RuntimeException("Missing video URN or upload URL in response");
            }

            // Store upload URL temporarily (we'll use it in next step)
            // For simplicity, we'll pass it through the videoUrn return value
            // In production, you might want to store this in a cache/redis
            log.debug("Got upload URL: {}", uploadUrl);
            
            // Store upload URL in a class variable or return both
            // For now, we'll call upload immediately, so we pass uploadUrl via another call
            uploadUrlCache.put(videoUrn, uploadUrl);

            return videoUrn;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("LinkedIn API Error - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("LinkedIn video upload initialization failed: " +
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to initialize video upload: {}", e.getMessage(), e);
            throw new RuntimeException("Video upload initialization failed", e);
        }
    }

    // Simple cache for upload URLs (in production, use Redis)
    private final Map<String, String> uploadUrlCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * STEP 2: Upload video bytes to LinkedIn
     * Uses the upload URL from initialization
     */
    private void uploadVideoToLinkedIn(byte[] videoBytes, String videoUrn, String accessToken) {
        try {
            String uploadUrl = uploadUrlCache.get(videoUrn);
            if (uploadUrl == null) {
                throw new RuntimeException("Upload URL not found for videoUrn: " + videoUrn);
            }

            log.info("Uploading {} bytes to LinkedIn...", videoBytes.length);

            // Upload video bytes
            HttpHeaders headers = new HttpHeaders();
            // IMPORTANT: No Authorization header for the upload URL
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(videoBytes.length);

            HttpEntity<byte[]> request = new HttpEntity<>(videoBytes, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                uploadUrl,
                HttpMethod.PUT,
                request,
                String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Video upload failed with status: " + response.getStatusCode());
            }

            log.info("Video uploaded successfully to LinkedIn");

            // Clean up cache
            uploadUrlCache.remove(videoUrn);

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("LinkedIn Video Upload Error - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("LinkedIn video upload failed: " +
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to upload video to LinkedIn: {}", e.getMessage(), e);
            throw new RuntimeException("Video upload to LinkedIn failed", e);
        }
    }

    /**
     * STEP 3: Wait for video processing
     * LinkedIn processes videos asynchronously
     */
    private void waitForVideoProcessing(String videoUrn, String accessToken) {
        try {
            int maxAttempts = 60; // 5 minutes max (5 seconds * 60)
            int attemptCount = 0;

            // Extract video ID from URN (format: urn:li:video:123456)
            String videoId = videoUrn.substring(videoUrn.lastIndexOf(":") + 1);

            while (attemptCount < maxAttempts) {
                String status = checkVideoStatus(videoId, accessToken);

                if ("AVAILABLE".equalsIgnoreCase(status) || "READY".equalsIgnoreCase(status)) {
                    log.info("Video processing completed successfully");
                    return;
                } else if ("FAILED".equalsIgnoreCase(status)) {
                    throw new RuntimeException("Video processing failed on LinkedIn");
                } else if ("PROCESSING".equalsIgnoreCase(status) || "UPLOADED".equalsIgnoreCase(status)) {
                    log.debug("Video still processing, waiting... (attempt {}/{})", attemptCount + 1, maxAttempts);
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
    private String checkVideoStatus(String videoId, String accessToken) {
        try {
            String url = VIDEO_STATUS_URL + videoId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("LinkedIn-Version", "202511");
            headers.set("X-RestLi-Protocol-Version", "2.0.0");

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Failed to check video status");
            }

            Map<String, Object> responseBody = response.getBody();
            String status = (String) responseBody.get("status");

            if (status != null) {
                return status;
            }

            // Fallback to check recipes status
            List<Map<String, Object>> recipes = (List<Map<String, Object>>) responseBody.get("recipes");
            if (recipes != null && !recipes.isEmpty()) {
                Map<String, Object> recipe = recipes.get(0);
                status = (String) recipe.get("status");
                if (status != null) {
                    return status;
                }
            }

            return "PROCESSING";

        } catch (Exception e) {
            log.error("Failed to check video status: {}", e.getMessage(), e);
            // Return PROCESSING to continue waiting rather than failing immediately
            return "PROCESSING";
        }
    }

    /**
     * STEP 4: Create LinkedIn post with video
     */
    private void createPostWithVideo(PostEntity post,
                                    String videoUrn,
                                    String accessToken,
                                    String linkedInUserId) {
        try {
            log.info("Creating LinkedIn post with video");

            String postText = post.getDescription() != null ? post.getDescription() : "";

            // Build post request body
            Map<String, Object> body = Map.of(
                "author", "urn:li:person:" + linkedInUserId,
                "commentary", postText,
                "visibility", "PUBLIC",
                "distribution", Map.of(
                    "feedDistribution", "MAIN_FEED",
                    "targetEntities", List.of(),
                    "thirdPartyDistributionChannels", List.of()
                ),
                "content", Map.of(
                    "media", Map.of(
                        "title", post.getTitle() != null ? post.getTitle() : "",
                        "id", videoUrn  // Use video URN
                    )
                ),
                "lifecycleState", "PUBLISHED",
                "isReshareDisabledByAuthor", false
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("LinkedIn-Version", "202511");
            headers.set("X-RestLi-Protocol-Version", "2.0.0");

            HttpEntity<Object> entity = new HttpEntity<>(body, headers);

            log.debug("Sending post creation request to LinkedIn");
            ResponseEntity<String> response = restTemplate.exchange(
                POST_CREATE_URL,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("LinkedIn post creation failed with status: " +
                        response.getStatusCode());
            }

            log.info("LinkedIn post with video created successfully - Response: {}", response.getBody());

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("LinkedIn API Error - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("LinkedIn post creation failed: " +
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to create LinkedIn post with video: {}", e.getMessage(), e);
            throw new RuntimeException("Post creation failed", e);
        }
    }
}