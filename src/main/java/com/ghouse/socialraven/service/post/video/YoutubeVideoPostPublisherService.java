package com.ghouse.socialraven.service.post.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostCollectionEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.service.provider.YouTubeOAuthService;
import com.ghouse.socialraven.service.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class YoutubeVideoPostPublisherService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private YouTubeOAuthService youtubeOAuthService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private ObjectMapper objectMapper;

    // YouTube API endpoints
    private static final String YOUTUBE_UPLOAD_URL = "https://www.googleapis.com/upload/youtube/v3/videos";
    private static final String YOUTUBE_API_URL = "https://www.googleapis.com/youtube/v3/videos";

    // YouTube video limits
    private static final long MAX_VIDEO_SIZE = 128L * 1024 * 1024 * 1024; // 128GB
    private static final int CHUNK_SIZE = 10 * 1024 * 1024; // 10MB chunks for resumable upload

    /**
     * Main entry point for publishing video to YouTube
     */
    public String postVideoToYoutube(PostEntity post,
                                     List<PostMediaEntity> mediaFiles,
                                     OAuthInfoEntity authInfo, PostCollectionEntity postCollection) {
        try {
            log.info("=== YouTube Video Post Started ===");
            log.info("PostID: {}, YouTubeChannelID: {}", post.getId(), authInfo.getProviderUserId());

            // Step 0: Validate inputs
            validateInputs(post, mediaFiles);

            // Get valid OAuth token
            OAuthInfoEntity validOAuthInfo = youtubeOAuthService.getValidOAuthInfo(authInfo);
            String accessToken = validOAuthInfo.getAccessToken();
            
            log.info("OAuth token validated successfully");
            log.debug("Access token present: {}", accessToken != null && !accessToken.isEmpty());

            // YouTube allows only 1 video per upload
            PostMediaEntity videoMedia = mediaFiles.get(0);
            log.info("Processing video file: {}", videoMedia.getFileKey());

            // Step 1: Download video bytes from S3
            log.info("Step 1: Downloading video from S3...");
            byte[] videoBytes = storageService.downloadFileBytes(videoMedia.getFileKey());
            log.info("Successfully downloaded {} bytes from S3 for: {}", 
                    videoBytes.length, videoMedia.getFileKey());

            // Validate video size
            if (videoBytes.length > MAX_VIDEO_SIZE) {
                log.error("Video exceeds YouTube's 128GB limit - Size: {} bytes, Limit: {} bytes",
                        videoBytes.length, MAX_VIDEO_SIZE);
                throw new RuntimeException("Video exceeds YouTube's 128GB limit: " + videoMedia.getFileKey());
            }

            // Step 2: Initialize resumable upload and get upload URL
            log.info("Step 2: Initializing resumable upload...");
            String uploadUrl = initializeResumableUpload(post, videoMedia, accessToken, videoBytes.length, postCollection);
            log.info("Resumable upload initialized successfully");
            log.debug("Upload URL obtained: {}", uploadUrl.substring(0, Math.min(50, uploadUrl.length())) + "...");

            // Step 3: Upload video in chunks
            log.info("Step 3: Starting chunked video upload...");
            log.info("Total size: {} bytes, Chunk size: {} bytes, Expected chunks: {}", 
                    videoBytes.length, CHUNK_SIZE, (videoBytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE);
            
            String videoId = uploadVideoInChunks(uploadUrl, videoBytes, accessToken);
            log.info("Video uploaded successfully - VideoID: {}", videoId);

            // Step 4: Verify video upload
            log.info("Step 4: Verifying video upload...");
            verifyVideoUpload(videoId, accessToken);
            log.info("Video verification completed successfully");

            log.info("=== YouTube Video Post Success ===");
            log.info("PostID: {}, VideoID: {}", post.getId(), videoId);
            log.info("Video URL: https://www.youtube.com/watch?v={}", videoId);

            return videoId;

        } catch (IllegalArgumentException e) {
            log.error("=== YouTube Video Post Failed - Invalid Input ===");
            log.error("PostID: {}, Error: {}", post.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("=== YouTube Video Post Failed ===");
            log.error("PostID: {}, Error: {}", post.getId(), e.getMessage(), e);
            throw new RuntimeException("YouTube Video Post Failed: " + post.getId(), e);
        }
    }

    /**
     * Validate inputs before processing
     */
    private void validateInputs(PostEntity post, List<PostMediaEntity> mediaFiles) {
        log.debug("Validating inputs...");
        
        if (post == null) {
            log.error("Post entity is null");
            throw new IllegalArgumentException("Post cannot be null");
        }
        
        if (mediaFiles == null || mediaFiles.isEmpty()) {
            log.error("No media files provided for PostID: {}", post.getId());
            throw new IllegalArgumentException("Cannot post to YouTube without media files");
        }

        if (mediaFiles.size() > 1) {
            log.error("Multiple videos provided - PostID: {}, Count: {}", post.getId(), mediaFiles.size());
            throw new IllegalArgumentException("YouTube allows only 1 video per upload. Received: " + mediaFiles.size());
        }

        PostMediaEntity videoMedia = mediaFiles.get(0);
        if (videoMedia.getFileKey() == null || videoMedia.getFileKey().isEmpty()) {
            log.error("Video file key is null or empty");
            throw new IllegalArgumentException("Video file key cannot be null or empty");
        }

        log.debug("Input validation passed");
    }

    /**
     * Initialize resumable upload session with YouTube
     * Returns the upload URL for chunked uploading
     */
    private String initializeResumableUpload(PostEntity post,
                                             PostMediaEntity videoMedia,
                                             String accessToken,
                                             long videoSize, PostCollectionEntity postCollection) {
        try {
            log.info("Initializing resumable upload session...");
            log.debug("Video size: {} bytes", videoSize);

            // Build video metadata
            Map<String, Object> snippet = buildVideoSnippet(post, postCollection);
            Map<String, Object> status = buildVideoStatus(post);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("snippet", snippet);
            metadata.put("status", status);

            String metadataJson = objectMapper.writeValueAsString(metadata);
            log.debug("Video metadata prepared: {}", metadataJson);

            // Determine content type
            String contentType = determineContentType(videoMedia);
            log.debug("Detected content type: {}", contentType);

            // Build URL with query parameters
            String url = YOUTUBE_UPLOAD_URL + "?uploadType=resumable&part=snippet,status";
            log.debug("Upload initialization URL: {}", url);

            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Type", "application/json; charset=UTF-8");
            headers.set("X-Upload-Content-Length", String.valueOf(videoSize));
            headers.set("X-Upload-Content-Type", contentType);

            log.debug("Request headers prepared - Auth present: {}, Content-Length: {}, Content-Type: {}",
                    headers.containsKey("Authorization"), videoSize, contentType);

            HttpEntity<String> request = new HttpEntity<>(metadataJson, headers);

            // Send initialization request
            log.info("Sending resumable upload initialization request to YouTube...");
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
            );

            log.info("Initialization response received - Status: {}", response.getStatusCode());

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to initialize resumable upload - Status: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("Failed to initialize resumable upload");
            }

            // Extract upload URL from Location header
            String uploadUrl = response.getHeaders().getFirst("Location");
            if (uploadUrl == null || uploadUrl.isEmpty()) {
                log.error("No Location header in response - Headers: {}", response.getHeaders());
                throw new RuntimeException("No upload URL in response");
            }

            log.info("Resumable upload session created successfully");
            log.debug("Upload URL: {}", uploadUrl);

            return uploadUrl;

        } catch (HttpClientErrorException e) {
            log.error("YouTube API Error during initialization - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to initialize YouTube upload: " +
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to initialize resumable upload: {}", e.getMessage(), e);
            throw new RuntimeException("Resumable upload initialization failed", e);
        }
    }

    /**
     * Upload video in chunks using resumable upload
     */
    private String uploadVideoInChunks(String uploadUrl, byte[] videoBytes, String accessToken) {
        try {
            log.info("Starting chunked video upload...");
            log.info("Total video size: {} bytes ({} MB)", 
                    videoBytes.length, videoBytes.length / (1024 * 1024));

            int totalChunks = (int) Math.ceil((double) videoBytes.length / CHUNK_SIZE);
            log.info("Video will be uploaded in {} chunks of {} bytes each", totalChunks, CHUNK_SIZE);

            int offset = 0;
            int chunkIndex = 0;

            while (offset < videoBytes.length) {
                int chunkSize = Math.min(CHUNK_SIZE, videoBytes.length - offset);
                byte[] chunk = Arrays.copyOfRange(videoBytes, offset, offset + chunkSize);

                log.info("Uploading chunk {}/{} - Size: {} bytes, Offset: {}, Range: {}-{}",
                        chunkIndex + 1, totalChunks, chunkSize, offset, offset, offset + chunkSize - 1);

                uploadChunk(uploadUrl, chunk, offset, videoBytes.length, accessToken, chunkIndex + 1, totalChunks);

                offset += chunkSize;
                chunkIndex++;

                // Calculate and log progress
                int progressPercent = (int) ((offset * 100.0) / videoBytes.length);
                log.info("Upload progress: {}% ({}/{} bytes)", 
                        progressPercent, offset, videoBytes.length);
            }

            log.info("All chunks uploaded successfully - Total: {} chunks", chunkIndex);

            // The last chunk response should contain the video ID
            // We need to make one more request to get the final response
            log.info("Finalizing upload and retrieving video ID...");
            String videoId = finalizeUpload(uploadUrl, videoBytes.length, accessToken);
            
            log.info("Video upload completed - VideoID: {}", videoId);
            return videoId;

        } catch (Exception e) {
            log.error("Failed to upload video in chunks: {}", e.getMessage(), e);
            throw new RuntimeException("Chunked video upload failed", e);
        }
    }

    /**
     * Upload a single chunk
     */
    private void uploadChunk(String uploadUrl,
                            byte[] chunk,
                            int offset,
                            long totalSize,
                            String accessToken,
                            int chunkNumber,
                            int totalChunks) {
        try {
            log.debug("Preparing chunk {} upload - Offset: {}, Size: {}", 
                    chunkNumber, offset, chunk.length);

            int rangeEnd = offset + chunk.length - 1;
            String contentRange = String.format("bytes %d-%d/%d", offset, rangeEnd, totalSize);
            
            log.debug("Content-Range header: {}", contentRange);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Length", String.valueOf(chunk.length));
            headers.set("Content-Range", contentRange);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            HttpEntity<byte[]> request = new HttpEntity<>(chunk, headers);

            log.debug("Sending chunk {} to YouTube...", chunkNumber);
            ResponseEntity<String> response = restTemplate.exchange(
                uploadUrl,
                HttpMethod.PUT,
                request,
                String.class
            );

            log.info("Chunk {} uploaded - Status: {}, Response length: {} bytes",
                    chunkNumber, response.getStatusCode(), 
                    response.getBody() != null ? response.getBody().length() : 0);

            // YouTube returns 308 Resume Incomplete for intermediate chunks
            // and 200/201 for the final chunk
            if (response.getStatusCode().value() == 308) {
                log.debug("Chunk {} upload acknowledged (308 Resume Incomplete)", chunkNumber);
            } else if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Chunk {} upload successful with final response", chunkNumber);
            } else {
                log.error("Unexpected status code for chunk {}: {}", 
                        chunkNumber, response.getStatusCode());
                throw new RuntimeException("Chunk upload failed with status: " + response.getStatusCode());
            }

        } catch (HttpClientErrorException e) {
            log.error("YouTube API Error during chunk {} upload - Status: {}, Body: {}",
                    chunkNumber, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to upload chunk " + chunkNumber + ": " +
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to upload chunk {}: {}", chunkNumber, e.getMessage(), e);
            throw new RuntimeException("Chunk " + chunkNumber + " upload failed", e);
        }
    }

    /**
     * Finalize upload and get video ID
     */
    private String finalizeUpload(String uploadUrl, long totalSize, String accessToken) {
        try {
            log.info("Finalizing upload...");

            // Send a PUT request with content range indicating completion
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Content-Length", "0");
            headers.set("Content-Range", String.format("bytes */%d", totalSize));

            HttpEntity<byte[]> request = new HttpEntity<>(new byte[0], headers);

            log.debug("Sending finalization request...");
            ResponseEntity<Map> response = restTemplate.exchange(
                uploadUrl,
                HttpMethod.PUT,
                request,
                Map.class
            );

            log.info("Finalization response received - Status: {}", response.getStatusCode());

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to finalize upload - Status: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("Failed to finalize upload");
            }

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                log.error("Empty response body from finalization request");
                throw new RuntimeException("Empty response from YouTube");
            }

            log.debug("Finalization response body: {}", responseBody);

            String videoId = (String) responseBody.get("id");
            if (videoId == null || videoId.isEmpty()) {
                log.error("No video ID in finalization response - Body: {}", responseBody);
                throw new RuntimeException("No video ID in response");
            }

            log.info("Upload finalized successfully - VideoID: {}", videoId);
            return videoId;

        } catch (Exception e) {
            log.error("Failed to finalize upload: {}", e.getMessage(), e);
            throw new RuntimeException("Upload finalization failed", e);
        }
    }

    /**
     * Verify video upload by fetching video details
     */
    private void verifyVideoUpload(String videoId, String accessToken) {
        try {
            log.info("Verifying video upload for VideoID: {}", videoId);

            String url = YOUTUBE_API_URL + "?part=snippet,status&id=" + videoId;
            log.debug("Verification URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            log.debug("Sending verification request...");
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class
            );

            log.info("Verification response received - Status: {}", response.getStatusCode());

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Video verification failed - Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to verify video upload");
            }

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                log.error("Empty response body from verification request");
                throw new RuntimeException("Empty verification response");
            }

            log.debug("Verification response: {}", responseBody);

            List<Map<String, Object>> items = (List<Map<String, Object>>) responseBody.get("items");
            if (items == null || items.isEmpty()) {
                log.error("No video found in verification response - VideoID: {}", videoId);
                throw new RuntimeException("Video not found after upload");
            }

            Map<String, Object> video = items.get(0);
            Map<String, Object> snippet = (Map<String, Object>) video.get("snippet");
            Map<String, Object> status = (Map<String, Object>) video.get("status");

            log.info("Video verified successfully:");
            log.info("  - Title: {}", snippet != null ? snippet.get("title") : "N/A");
            log.info("  - Upload Status: {}", status != null ? status.get("uploadStatus") : "N/A");
            log.info("  - Privacy Status: {}", status != null ? status.get("privacyStatus") : "N/A");

        } catch (Exception e) {
            log.error("Failed to verify video upload: {}", e.getMessage(), e);
            // Don't throw here - verification failure shouldn't fail the entire upload
            log.warn("Video upload completed but verification failed - VideoID: {}", videoId);
        }
    }

    /**
     * Build video snippet (title, description, tags, etc.)
     */
    private Map<String, Object> buildVideoSnippet(PostEntity post, PostCollectionEntity postCollection) {
        log.debug("Building video snippet...");

        Map<String, Object> snippet = new LinkedHashMap<>();
        
        // Title (required)
        String title = postCollection.getTitle() != null && !postCollection.getTitle().isEmpty()
                ? postCollection.getTitle()
                : "Video Upload";
        
        if (title.length() > 100) {
            log.warn("Title exceeds 100 characters, truncating: {}", title);
            title = title.substring(0, 97) + "...";
        }
        snippet.put("title", title);
        log.debug("Video title: {}", title);

        // Description (optional)
        String description = postCollection.getDescription() != null ? postCollection.getDescription() : "";
        if (description.length() > 5000) {
            log.warn("Description exceeds 5000 characters, truncating");
            description = description.substring(0, 4997) + "...";
        }
        snippet.put("description", description);
        log.debug("Video description length: {} characters", description.length());

        // Category (optional) - default to Entertainment (24)
        snippet.put("categoryId", "24");
        log.debug("Video category: Entertainment (24)");

        return snippet;
    }

    /**
     * Build video status (privacy, embeddable, etc.)
     */
    private Map<String, Object> buildVideoStatus(PostEntity post) {
        log.debug("Building video status...");

        Map<String, Object> status = new LinkedHashMap<>();
        
        // Privacy status - default to public
        // Options: "public", "private", "unlisted"
        String privacyStatus = "public"; // You can make this configurable
        status.put("privacyStatus", privacyStatus);
        log.debug("Privacy status: {}", privacyStatus);

        // Make embeddable
        status.put("embeddable", true);
        
        // Allow comments
        status.put("publicStatsViewable", true);

        log.debug("Video status configured");
        return status;
    }

    /**
     * Determine content type from file extension or media entity
     */
    private String determineContentType(PostMediaEntity videoMedia) {
        log.debug("Determining content type for: {}", videoMedia.getFileKey());

        // Fall back to file extension
        String fileName = videoMedia.getFileName().toLowerCase();
        String contentType;

        if (fileName.endsWith(".mp4")) {
            contentType = "video/mp4";
        } else if (fileName.endsWith(".mov")) {
            contentType = "video/quicktime";
        } else if (fileName.endsWith(".avi")) {
            contentType = "video/x-msvideo";
        } else if (fileName.endsWith(".wmv")) {
            contentType = "video/x-ms-wmv";
        } else if (fileName.endsWith(".flv")) {
            contentType = "video/x-flv";
        } else if (fileName.endsWith(".webm")) {
            contentType = "video/webm";
        } else if (fileName.endsWith(".mkv")) {
            contentType = "video/x-matroska";
        } else if (fileName.endsWith(".3gp")) {
            contentType = "video/3gpp";
        } else {
            log.warn("Unknown file extension, defaulting to video/mp4");
            contentType = "video/mp4";
        }

        log.debug("Determined content type: {}", contentType);
        return contentType;
    }
}