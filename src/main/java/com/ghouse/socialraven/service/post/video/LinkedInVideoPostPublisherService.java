package com.ghouse.socialraven.service.post.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostCollectionEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.service.provider.LinkedInOAuthService;
import com.ghouse.socialraven.service.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private static final String VIDEO_FINALIZE_URL = "https://api.linkedin.com/rest/videos?action=finalizeUpload";
    private static final String POST_CREATE_URL = "https://api.linkedin.com/rest/posts";

    // LinkedIn video limits
    private static final long MAX_VIDEO_SIZE = 5L * 1024 * 1024 * 1024; // 5GB
    private static final long MIN_VIDEO_SIZE = 75 * 1024; // 75KB

    // Cache for upload instructions (in production, use Redis)
    private final Map<String, List<Map<String, Object>>> uploadInstructionsCache = new java.util.concurrent.ConcurrentHashMap<>();

    // Cache for uploaded part ETags
    private final Map<String, List<String>> uploadedPartsCache = new java.util.concurrent.ConcurrentHashMap<>();

    public void postVideosToLinkedIn(PostEntity post,
                                     List<PostMediaEntity> mediaFiles,
                                     OAuthInfoEntity authInfo,
                                     PostCollectionEntity postCollection) {
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

            // Step 4: Finalize the upload (CRITICAL - tells LinkedIn upload is complete)
            finalizeVideoUpload(videoUrn, accessToken);
            log.info("Video upload finalized");

            // Step 5: Wait a bit for LinkedIn to process
            log.info("Waiting 5 seconds for LinkedIn to register the finalized video...");
            Thread.sleep(5000);

            // Step 6: Create post with video
            log.info("Creating LinkedIn post with video...");
            createPostWithVideo(post, videoUrn, accessToken, linkedInUserId, postCollection);

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

            // Log the full response for debugging
            log.info("LinkedIn video init response: {}", responseBody);

            // Try different possible response structures
            String videoUrn = null;
            String uploadUrl = null;

            // Structure 1: value.video and value.uploadUrl
            if (responseBody.containsKey("value")) {
                Map<String, Object> value = (Map<String, Object>) responseBody.get("value");
                if (value != null) {
                    videoUrn = (String) value.get("video");
                    uploadUrl = (String) value.get("uploadUrl");
                }
            }

            // Structure 2: Direct keys
            if (videoUrn == null && responseBody.containsKey("video")) {
                videoUrn = (String) responseBody.get("video");
            }
            if (uploadUrl == null && responseBody.containsKey("uploadUrl")) {
                uploadUrl = (String) responseBody.get("uploadUrl");
            }

            // Structure 3: uploadInstructions array
            List<Map<String, Object>> uploadInstructions = null;
            if (responseBody.containsKey("uploadInstructions")) {
                uploadInstructions = (List<Map<String, Object>>) responseBody.get("uploadInstructions");
            }

            // Structure 4: Check if value has uploadInstructions
            if (uploadInstructions == null && responseBody.containsKey("value")) {
                Map<String, Object> value = (Map<String, Object>) responseBody.get("value");
                if (value != null && value.containsKey("uploadInstructions")) {
                    uploadInstructions = (List<Map<String, Object>>) value.get("uploadInstructions");
                }
            }

            // For backward compatibility, if we have a single uploadUrl, create instructions
            if (uploadInstructions == null && uploadUrl != null) {
                uploadInstructions = List.of(
                        Map.of(
                                "uploadUrl", uploadUrl,
                                "firstByte", 0,
                                "lastByte", (int) fileSizeBytes - 1
                        )
                );
            }

            if (videoUrn == null || uploadInstructions == null || uploadInstructions.isEmpty()) {
                log.error("Could not extract video URN or upload instructions from response: {}", responseBody);
                throw new RuntimeException("Missing video URN or upload instructions in response. Response keys: " + responseBody.keySet());
            }

            log.info("Successfully extracted - VideoURN: {}, Upload chunks: {}", videoUrn, uploadInstructions.size());

            // Store upload instructions temporarily
            uploadInstructionsCache.put(videoUrn, uploadInstructions);

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

    /**
     * STEP 2: Upload video to LinkedIn using chunked upload
     * LinkedIn uses CHUNKED upload for videos > 4MB
     */
    private void uploadVideoToLinkedIn(byte[] videoBytes, String videoUrn, String accessToken) {
        try {
            // Get upload instructions from cache
            List<Map<String, Object>> uploadInstructions = uploadInstructionsCache.get(videoUrn);

            if (uploadInstructions == null || uploadInstructions.isEmpty()) {
                throw new RuntimeException("Upload instructions not found for videoUrn: " + videoUrn);
            }

            log.info("Uploading {} bytes to LinkedIn in {} chunks...", videoBytes.length, uploadInstructions.size());

            // List to store ETags from successful uploads
            List<String> uploadedPartETags = new java.util.ArrayList<>();

            // Upload each chunk
            for (int i = 0; i < uploadInstructions.size(); i++) {
                Map<String, Object> instruction = uploadInstructions.get(i);
                String uploadUrl = (String) instruction.get("uploadUrl");

                // Get byte range for this chunk
                int firstByte = ((Number) instruction.get("firstByte")).intValue();
                int lastByte = ((Number) instruction.get("lastByte")).intValue();

                // Extract chunk
                int chunkSize = lastByte - firstByte + 1;
                byte[] chunk = new byte[chunkSize];
                System.arraycopy(videoBytes, firstByte, chunk, 0, chunkSize);

                log.info("Uploading chunk {}/{}: bytes {}-{} ({} bytes)",
                        i + 1, uploadInstructions.size(), firstByte, lastByte, chunkSize);

                // Upload chunk and get ETag
                String etag = uploadChunk(chunk, uploadUrl, i + 1, uploadInstructions.size());
                uploadedPartETags.add(etag);

                log.info("Chunk {}/{} uploaded with ETag: {}", i + 1, uploadInstructions.size(), etag);
            }

            log.info("All chunks uploaded successfully to LinkedIn");

            // Store ETags for finalize step
            uploadedPartsCache.put(videoUrn, uploadedPartETags);

            // Clean up upload instructions cache
            uploadInstructionsCache.remove(videoUrn);

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
     * Upload a single chunk to LinkedIn using HttpURLConnection
     * CRITICAL: LinkedIn upload URLs are pre-signed and must NOT have ANY authentication headers
     * Using raw HttpURLConnection to avoid any RestTemplate interceptors or default headers
     * Returns the ETag from the response
     */
    private String uploadChunk(byte[] chunk, String uploadUrl, int chunkNumber, int totalChunks) {
        HttpURLConnection connection = null;
        try {
            log.debug("Uploading chunk {}/{} ({} bytes) to pre-signed URL", chunkNumber, totalChunks, chunk.length);

            // Use HttpURLConnection directly to avoid any interceptors
            URL url = new URL(uploadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);

            // ONLY these headers - NO Authorization or Bearer token!
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("Content-Length", String.valueOf(chunk.length));

            // Write chunk data
            try (OutputStream os = connection.getOutputStream()) {
                os.write(chunk);
                os.flush();
            }

            // Check response
            int responseCode = connection.getResponseCode();

            if (responseCode < 200 || responseCode >= 300) {
                String errorBody = "";
                try {
                    errorBody = new String(connection.getErrorStream().readAllBytes());
                } catch (Exception e) {
                    // Ignore error reading error stream
                }

                log.error("Failed to upload chunk {}/{} - Status: {}, Body: {}",
                        chunkNumber, totalChunks, responseCode, errorBody);
                throw new RuntimeException("Chunk upload failed: " + responseCode + " " + connection.getResponseMessage());
            }

            // Get ETag from response headers (critical for finalizing upload)
            String etag = connection.getHeaderField("ETag");
            if (etag == null || etag.isEmpty()) {
                // Fallback: some services return it as 'Etag' or 'etag'
                etag = connection.getHeaderField("Etag");
                if (etag == null) {
                    etag = connection.getHeaderField("etag");
                }
            }

            // Remove quotes from ETag if present
            if (etag != null && etag.startsWith("\"") && etag.endsWith("\"")) {
                etag = etag.substring(1, etag.length() - 1);
            }

            log.debug("Chunk {}/{} uploaded successfully - Status: {}, ETag: {}",
                    chunkNumber, totalChunks, responseCode, etag);

            return etag != null ? etag : "";

        } catch (IOException e) {
            log.error("Failed to upload chunk {}/{}: {}", chunkNumber, totalChunks, e.getMessage(), e);
            throw new RuntimeException("Chunk upload failed: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * STEP 3: Finalize video upload
     * This tells LinkedIn the upload is complete and the video is ready to use
     */
    private void finalizeVideoUpload(String videoUrn, String accessToken) {
        try {
            String url = VIDEO_FINALIZE_URL;

            // Get uploaded part ETags from cache
            List<String> uploadedPartETags = uploadedPartsCache.get(videoUrn);

            if (uploadedPartETags == null || uploadedPartETags.isEmpty()) {
                throw new RuntimeException("No uploaded parts found for videoUrn: " + videoUrn);
            }

            // Get upload token from the init response (if it exists)
            String uploadToken = ""; // LinkedIn often doesn't require this

            // Build finalize request body with uploadedPartIds
            Map<String, Object> body = Map.of(
                    "finalizeUploadRequest", Map.of(
                            "video", videoUrn,
                            "uploadToken", uploadToken,
                            "uploadedPartIds", uploadedPartETags
                    )
            );

            log.info("Finalizing upload for video URN: {}", videoUrn);
            log.debug("Uploaded part ETags: {}", uploadedPartETags);
            log.debug("Finalize request body: {}", body);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("LinkedIn-Version", "202511");
            headers.set("X-RestLi-Protocol-Version", "2.0.0");

            HttpEntity<Object> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to finalize video upload");
            }

            log.info("✓ Video upload finalized successfully");
            log.debug("Finalize response: {}", response.getBody());

            // Clean up cache
            uploadedPartsCache.remove(videoUrn);

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("LinkedIn API Error during finalize - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("LinkedIn video finalize failed: " +
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to finalize video upload: {}", e.getMessage(), e);
            throw new RuntimeException("Video finalize failed", e);
        }
    }

    /**
     * STEP 4: Create LinkedIn post with video
     */
    private void createPostWithVideo(PostEntity post,
                                     String videoUrn,
                                     String accessToken,
                                     String linkedInUserId, PostCollectionEntity postCollection) {
        try {
            log.info("Creating LinkedIn post with video");
            log.debug("Video URN: {}", videoUrn);
            log.debug("LinkedIn User ID: {}", linkedInUserId);
            log.debug("Post Title: {}", postCollection.getTitle());
            log.debug("Post Description: {}", postCollection.getDescription());

            String postText = postCollection.getDescription() != null ? postCollection.getDescription() : "";

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
                                    "title", postCollection.getTitle() != null ? postCollection.getTitle() : "",
                                    "id", videoUrn  // Use video URN
                            )
                    ),
                    "lifecycleState", "PUBLISHED",
                    "isReshareDisabledByAuthor", false
            );

            log.debug("Post request body: {}", body);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("LinkedIn-Version", "202511");
            headers.set("X-RestLi-Protocol-Version", "2.0.0");

            HttpEntity<Object> entity = new HttpEntity<>(body, headers);

            log.debug("Sending post creation request to LinkedIn: {}", POST_CREATE_URL);
            ResponseEntity<String> response = restTemplate.exchange(
                    POST_CREATE_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            log.info("LinkedIn post creation response code: {}", response.getStatusCode());

            // Extract post URN from Location header
            String locationHeader = response.getHeaders().getFirst("Location");
            String xLinkedInId = response.getHeaders().getFirst("x-linkedin-id");

            if (locationHeader != null) {
                log.info("LinkedIn post created at: {}", locationHeader);
            }
            if (xLinkedInId != null) {
                log.info("LinkedIn post ID: {}", xLinkedInId);
            }

            String responseBody = response.getBody();
            if (responseBody != null && !responseBody.isEmpty()) {
                log.info("LinkedIn post creation response body: {}", responseBody);
            }

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("LinkedIn post creation failed with status: " +
                        response.getStatusCode());
            }

            log.info("✓ LinkedIn video post created successfully!");

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