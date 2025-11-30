package com.ghouse.socialraven.service.post;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.dto.LinkedInUploadInfo;
import com.ghouse.socialraven.dto.LinkedInUploadResponse;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.repo.PostRepo;
import com.ghouse.socialraven.service.provider.LinkedInOAuthService;
import com.ghouse.socialraven.service.provider.XOAuthService;
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
public class ImagePostPublisherService {

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private OAuthInfoRepo oAuthInfoRepo;

    @Autowired
    private LinkedInOAuthService linkedInOAuthService;

    @Autowired
    private XOAuthService xOAuthService;

    @Autowired
    private StorageService storageService;


    public void publishPost(PostEntity post) {
        try{
            String userId = post.getUserId();
            log.info("Publishing Image(s) post, title:{} for userId: {} ", post.getTitle(), userId);
            List<String> providerUserIds = post.getProviderUserIds();
            List<OAuthInfoEntity> oauthInfos = oAuthInfoRepo.findByUserIdAndProviderUserIdIn(userId, providerUserIds);

            List<PostMediaEntity> mediaFiles = post.getMediaFiles();
            for(var authInfo : oauthInfos){
                if(Provider.LINKEDIN.equals(authInfo.getProvider())){
                    postImagesToLinkedin(post, mediaFiles, authInfo);
                }
                if(Provider.X.equals(authInfo.getProvider())){
                    postImagesToX(post,mediaFiles, authInfo);
                }
            }


            post.setPostStatus(PostStatus.POSTED);
            postRepo.save(post);
        } catch (Exception exp){
            throw new RuntimeException("Failed to Text Post: "+post.getId(), exp);
        }

    }

    public void postImagesToLinkedin(PostEntity post,
                                     List<PostMediaEntity> mediaFiles,
                                     OAuthInfoEntity authInfo) {
        try {
            OAuthInfoEntity validOAuthInfo = xOAuthService.getValidOAuthInfo(authInfo);
            String accessToken = validOAuthInfo.getAccessToken();
            String linkedinUserId = authInfo.getProviderUserId();

            log.info("Starting LinkedIn image post for postId: {}, mediaCount: {}",
                    post.getId(), mediaFiles.size());

            List<String> assetUrns = new ArrayList<>();

            for (int i = 0; i < mediaFiles.size(); i++) {
                PostMediaEntity media = mediaFiles.get(i);
                log.info("Processing media {}/{}: {}", i + 1, mediaFiles.size(), media.getFileKey());

                try {
                    // Step 1 → Generate S3 download URL
                    String presignedUrl = storageService.generatePresignedGetUrl(media.getFileKey());
                    log.debug("Generated presigned URL for media: {}", media.getFileKey());

                    // Step 2 → Register image upload on LinkedIn
                    LinkedInUploadInfo uploadInfo = registerImageUpload(accessToken, linkedinUserId);
                    log.info("Registered upload - Asset: {}", uploadInfo.getAsset());

                    // Step 3 → Upload bytes to LinkedIn
                    uploadImageToLinkedin(presignedUrl, uploadInfo.getUploadUrl());
                    log.info("Successfully uploaded image to LinkedIn");

                    // Step 4 → Collect LinkedIn asset URN
                    assetUrns.add(uploadInfo.getAsset());

                } catch (Exception e) {
                    log.error("Failed to upload media {}/{}: {}", i + 1, mediaFiles.size(),
                            media.getFileKey(), e);
                    throw new RuntimeException("Failed to upload media: " + media.getFileKey(), e);
                }
            }

            // Step 5 → Final UGC post
            log.info("Creating UGC post with {} assets", assetUrns.size());
            postUGCWithImages(accessToken, post, assetUrns, linkedinUserId);

            log.info("LinkedIn Image Post Success for post {} with {} assets",
                    post.getId(), assetUrns.size());

        } catch (Exception exp) {
            log.error("LinkedIn Image(s) Post Failed for postId: {} → {}",
                    post.getId(), exp.getMessage(), exp);
            throw new RuntimeException("LinkedIn Image(s) Post Failed: " + post.getId(), exp);
        }
    }

    // STEP 1: REGISTER UPLOAD
    private LinkedInUploadInfo registerImageUpload(String accessToken, String linkedinUserId) {
        String url = "https://api.linkedin.com/v2/assets?action=registerUpload";

        Map<String, Object> body = Map.of(
                "registerUploadRequest", Map.of(
                        "recipes", List.of("urn:li:digitalmediaRecipe:feedshare-image"),
                        "owner", "urn:li:person:" + linkedinUserId,
                        "serviceRelationships", List.of(
                                Map.of(
                                        "relationshipType", "OWNER",
                                        "identifier", "urn:li:userGeneratedContent"
                                )
                        )
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Restli-Protocol-Version", "2.0.0");

        HttpEntity<Object> request = new HttpEntity<>(body, headers);

        try {
            log.debug("Registering upload with LinkedIn API");
            ResponseEntity<LinkedInUploadResponse> response =
                    restTemplate.exchange(url, HttpMethod.POST, request, LinkedInUploadResponse.class);

            LinkedInUploadResponse resp = response.getBody();

            // Detailed validation
            if (resp == null) {
                throw new RuntimeException("LinkedIn upload response is null");
            }

            if (resp.getValue() == null) {
                log.error("LinkedIn response value is null. Full response: {}", resp);
                throw new RuntimeException("LinkedIn upload response value is null");
            }

            String asset = resp.getValue().getAsset();
            if (asset == null || asset.isEmpty()) {
                throw new RuntimeException("LinkedIn asset URN is empty");
            }

            // FIXED: Extract upload URL from the new structure
            Map<String, LinkedInUploadResponse.MediaUploadInfo> uploadMechanism =
                    resp.getValue().getUploadMechanism();

            if (uploadMechanism == null || uploadMechanism.isEmpty()) {
                log.error("Upload mechanism is empty. Response: {}", resp);
                throw new RuntimeException("LinkedIn upload mechanism not provided");
            }

            // Get the first (and usually only) upload mechanism
            LinkedInUploadResponse.MediaUploadInfo uploadInfo =
                    uploadMechanism.values().iterator().next();

            if (uploadInfo == null || uploadInfo.getMediaUploadHttpRequest() == null) {
                throw new RuntimeException("Media upload request is null");
            }

            String uploadUrl = uploadInfo.getMediaUploadHttpRequest().getUploadUrl();

            if (uploadUrl == null || uploadUrl.isEmpty()) {
                throw new RuntimeException("LinkedIn upload URL is empty");
            }

            log.debug("Upload registration successful - Asset: {}", asset);
            return new LinkedInUploadInfo(asset, uploadUrl);

        } catch (Exception e) {
            log.error("Failed to register upload with LinkedIn: {}", e.getMessage(), e);
            throw new RuntimeException("LinkedIn upload registration failed", e);
        }
    }

    // STEP 2: UPLOAD IMAGE TO LINKEDIN
    private void uploadImageToLinkedin(String presignedUrl, String uploadUrl) {
        try {
            log.debug("Downloading image from S3: {}", presignedUrl);

            // Download image from S3
            byte[] imageBytes = restTemplate.getForObject(presignedUrl, byte[].class);

            if (imageBytes == null || imageBytes.length == 0) {
                throw new RuntimeException("Downloaded image is empty");
            }

            log.debug("Downloaded image size: {} bytes", imageBytes.length);

            // Upload to LinkedIn
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(imageBytes.length);

            HttpEntity<byte[]> request = new HttpEntity<>(imageBytes, headers);

            log.debug("Uploading image to LinkedIn: {}", uploadUrl);
            ResponseEntity<String> res =
                    restTemplate.exchange(uploadUrl, HttpMethod.PUT, request, String.class);

            if (!res.getStatusCode().is2xxSuccessful()) {
                log.error("LinkedIn image upload failed with status: {}, body: {}",
                        res.getStatusCode(), res.getBody());
                throw new RuntimeException("LinkedIn image upload failed → " + res.getStatusCode());
            }

            log.debug("Image upload successful - Status: {}", res.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to upload image to LinkedIn: {}", e.getMessage(), e);
            throw new RuntimeException("Image upload to LinkedIn failed", e);
        }
    }

    // STEP 3: CREATE UGC POST
    private void postUGCWithImages(String accessToken,
                                   PostEntity post,
                                   List<String> assetUrns,
                                   String linkedinUserId) {
        String url = "https://api.linkedin.com/v2/ugcPosts";

        // Validate inputs
        if (assetUrns == null || assetUrns.isEmpty()) {
            throw new RuntimeException("Cannot create post without assets");
        }

        Map<String, Object> body = Map.of(
                "author", "urn:li:person:" + linkedinUserId,
                "lifecycleState", "PUBLISHED",
                "specificContent", Map.of(
                        "com.linkedin.ugc.ShareContent", Map.of(
                                "shareCommentary", Map.of("text",
                                        post.getDescription() != null ? post.getDescription() : ""),
                                "shareMediaCategory", "IMAGE",
                                "media", buildMediaList(assetUrns)
                        )
                ),
                "visibility", Map.of(
                        "com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Restli-Protocol-Version", "2.0.0");

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            log.debug("Creating UGC post on LinkedIn");
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("LinkedIn UGC post failed - Status: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                throw new RuntimeException("LinkedIn UGC post failed: " + response.getStatusCode());
            }

            log.info("UGC post created successfully - Response: {}", response.getBody());

        } catch (Exception e) {
            log.error("Failed to create UGC post: {}", e.getMessage(), e);
            throw new RuntimeException("LinkedIn UGC post creation failed", e);
        }
    }

    private List<Map<String, Object>> buildMediaList(List<String> assetUrns) {
        return assetUrns.stream()
                .map(urn -> {
                    if (urn == null || urn.isEmpty()) {
                        throw new RuntimeException("Asset URN is empty");
                    }
                    return Map.of(
                            "status", "READY",
                            "media", urn,
                            "title", Map.of("text", "")
                    );
                })
                .toList();
    }

    // ==================== UTILITY: SANITIZE FILENAME ====================
    /**
     * Sanitizes filename by replacing special characters with safe alternatives
     * Keeps alphanumeric, dots, hyphens, underscores. Replaces everything else.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }

        // Replace spaces and special chars with underscore
        // Keep only: letters, numbers, dots, hyphens, underscores
        String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Remove multiple consecutive underscores
        sanitized = sanitized.replaceAll("_{2,}", "_");

        // Remove leading/trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");

        log.debug("Sanitized filename: {} -> {}", filename, sanitized);
        return sanitized;
    }

    // Use this when generating file keys for storage
    public String generateSafeFileKey(String originalFilename, String prefix) {
        String sanitized = sanitizeFilename(originalFilename);
        long timestamp = System.currentTimeMillis();
        return String.format("%s/%d_%s", prefix, timestamp, sanitized);
    }






    public void postImagesToX(PostEntity post, List<PostMediaEntity> mediaFiles, OAuthInfoEntity authInfo) {
        try {
            OAuthInfoEntity validOAuthInfo = xOAuthService.getValidOAuthInfo(authInfo);
            String accessToken = validOAuthInfo.getAccessToken();

            //TODO- Implement

//            log.info("X Image(s) Posted Successfully → {}", response.getBody());
        } catch (Exception exp) {
            log.error("X Image(s) Post Failed → {}", exp.getMessage(), exp);
            throw new RuntimeException("X Image(s) Post Failed: "+post.getId(), exp);
        }
    }







}
