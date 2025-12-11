package com.ghouse.socialraven.service.post.image;

import com.ghouse.socialraven.dto.LinkedInImageUploadResponse;
import com.ghouse.socialraven.dto.LinkedInUploadInfo;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.service.provider.LinkedInOAuthService;
import com.ghouse.socialraven.service.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LinkedInImagePostPublisherService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LinkedInOAuthService linkedInOAuthService;

    @Autowired
    private StorageService storageService;


    public void postImagesToLinkedin(PostEntity post,
                                     List<PostMediaEntity> mediaFiles,
                                     OAuthInfoEntity authInfo) {
        try {
            // Step 0: Validate inputs
            if (mediaFiles == null || mediaFiles.isEmpty()) {
                throw new IllegalArgumentException("Cannot post to LinkedIn without media files");
            }

            // Get valid OAuth token
            OAuthInfoEntity validOAuthInfo = linkedInOAuthService.getValidOAuthInfo(authInfo);
            String accessToken = validOAuthInfo.getAccessToken();
            String linkedinUserId = authInfo.getProviderUserId();

            log.info("=== LinkedIn Image Post Started ===");
            log.info("PostID: {}, MediaCount: {}, LinkedInUserID: {}",
                    post.getId(), mediaFiles.size(), linkedinUserId);

            List<String> imageUrns = new ArrayList<>();

            // Process each image
            for (int i = 0; i < mediaFiles.size(); i++) {
                PostMediaEntity media = mediaFiles.get(i);
                int currentIndex = i + 1;

                log.info("Processing image {}/{}: {}", currentIndex, mediaFiles.size(), media.getFileKey());

                try {
                    // Step 1: Download image bytes directly from S3
                    byte[] imageBytes = storageService.downloadFileBytes(media.getFileKey());
                    log.debug("Downloaded {} bytes from S3 for: {}", imageBytes.length, media.getFileKey());

                    // Step 2: Register image upload with LinkedIn
                    LinkedInUploadInfo uploadInfo = registerImageUpload(accessToken, linkedinUserId);
                    log.info("Registered LinkedIn upload - ImageURN: {}", uploadInfo.getAsset());

                    // Step 3: Upload image bytes to LinkedIn
                    uploadImageToLinkedin(imageBytes, uploadInfo.getUploadUrl());
                    log.info("Successfully uploaded image {}/{} to LinkedIn", currentIndex, mediaFiles.size());

                    // Step 4: Collect LinkedIn image URN
                    imageUrns.add(uploadInfo.getAsset());

                } catch (Exception e) {
                    log.error("Failed to upload image {}/{}: {} - Error: {}",
                            currentIndex, mediaFiles.size(), media.getFileKey(), e.getMessage(), e);
                    throw new RuntimeException("Failed to upload media: " + media.getFileKey(), e);
                }
            }

            // Step 5: Create LinkedIn post with all uploaded images
            log.info("All {} images uploaded successfully. Creating LinkedIn post...", imageUrns.size());
            postUGCWithImages(accessToken, post, imageUrns, linkedinUserId);

            log.info("=== LinkedIn Image Post Success ===");
            log.info("PostID: {}, ImagesPosted: {}", post.getId(), imageUrns.size());

        } catch (IllegalArgumentException e) {
            log.error("Invalid input for LinkedIn post - PostID: {} - Error: {}",
                    post.getId(), e.getMessage());
            throw e;
        } catch (Exception exp) {
            log.error("=== LinkedIn Image Post Failed ===");
            log.error("PostID: {}, Error: {}", post.getId(), exp.getMessage(), exp);
            throw new RuntimeException("LinkedIn Image(s) Post Failed: " + post.getId(), exp);
        }
    }

    // STEP 1: REGISTER IMAGE UPLOAD
    private LinkedInUploadInfo registerImageUpload(String accessToken, String linkedinUserId) {
        String url = "https://api.linkedin.com/rest/images?action=initializeUpload";

        Map<String, Object> body = Map.of(
                "initializeUploadRequest", Map.of(
                        "owner", "urn:li:person:" + linkedinUserId
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("LinkedIn-Version", "202511");
        headers.set("X-RestLi-Protocol-Version", "2.0.0");

        HttpEntity<Object> request = new HttpEntity<>(body, headers);

        try {
            log.debug("Registering image upload with LinkedIn");
            ResponseEntity<LinkedInImageUploadResponse> response =
                    restTemplate.exchange(url, HttpMethod.POST, request, LinkedInImageUploadResponse.class);

            LinkedInImageUploadResponse resp = response.getBody();

            // Validate response
            if (resp == null || resp.getValue() == null) {
                throw new RuntimeException("LinkedIn upload response is invalid");
            }

            String imageUrn = resp.getValue().getImage();
            String uploadUrl = resp.getValue().getUploadUrl();

            if (imageUrn == null || imageUrn.isEmpty() || uploadUrl == null || uploadUrl.isEmpty()) {
                throw new RuntimeException("LinkedIn response missing image URN or upload URL");
            }

            log.debug("Upload registered - ImageURN: {}", imageUrn);
            return new LinkedInUploadInfo(imageUrn, uploadUrl);

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("LinkedIn API Error - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("LinkedIn upload registration failed: " +
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to register upload: {}", e.getMessage(), e);
            throw new RuntimeException("LinkedIn upload registration failed", e);
        }
    }

    // STEP 2: UPLOAD IMAGE TO LINKEDIN
    private void uploadImageToLinkedin(byte[] imageBytes, String uploadUrl) {
        try {
            if (imageBytes == null || imageBytes.length == 0) {
                throw new RuntimeException("Image bytes are empty or null");
            }

            log.debug("Uploading {} bytes to LinkedIn", imageBytes.length);

            // Upload to LinkedIn
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(imageBytes.length);

            HttpEntity<byte[]> request = new HttpEntity<>(imageBytes, headers);

            log.debug("Uploading {} bytes to LinkedIn", imageBytes.length);
            ResponseEntity<String> response =
                    restTemplate.exchange(uploadUrl, HttpMethod.PUT, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("LinkedIn image upload failed with status: " +
                        response.getStatusCode());
            }

            log.debug("Image uploaded successfully to LinkedIn");

        } catch (Exception e) {
            log.error("Failed to upload image to LinkedIn: {}", e.getMessage(), e);
            throw new RuntimeException("Image upload to LinkedIn failed", e);
        }
    }

    // STEP 3: CREATE POST WITH IMAGES
    private void postUGCWithImages(String accessToken,
                                   PostEntity post,
                                   List<String> imageUrns,
                                   String linkedinUserId) {

        String url = "https://api.linkedin.com/rest/posts";

        if (imageUrns == null || imageUrns.isEmpty()) {
            throw new RuntimeException("Cannot create LinkedIn post without image URNs");
        }

        log.info("Creating LinkedIn post with {} images", imageUrns.size());

        // Build post request body
        Map<String, Object> body = Map.of(
                "author", "urn:li:person:" + linkedinUserId,
                "commentary", post.getDescription() != null ? post.getDescription() : "",
                "visibility", "PUBLIC",
                "distribution", Map.of(
                        "feedDistribution", "MAIN_FEED",
                        "targetEntities", List.of(),
                        "thirdPartyDistributionChannels", List.of()
                ),
                "content", Map.of(
                        "media", Map.of(
                                "title", post.getTitle() != null ? post.getTitle() : "",
                                "id", imageUrns.get(0) // Use first image as primary
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

        try {
            log.debug("Sending post creation request to LinkedIn");
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("LinkedIn post creation failed with status: " +
                        response.getStatusCode());
            }

            log.info("LinkedIn post created successfully - Response: {}", response.getBody());

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("LinkedIn API Error - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("LinkedIn post creation failed: " +
                    e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to create LinkedIn post: {}", e.getMessage(), e);
            throw new RuntimeException("LinkedIn post creation failed", e);
        }
    }


}
