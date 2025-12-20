package com.ghouse.socialraven.dto;

import lombok.Data;

/**
 * Request to initialize LinkedIn video upload
 */
@Data
public class LinkedInVideoInitRequest {
    private InitializeUploadRequest initializeUploadRequest;

    @Data
    public static class InitializeUploadRequest {
        private String owner;               // urn:li:person:123456
        private Long fileSizeBytes;
        private Boolean uploadCaptions;
        private Boolean uploadThumbnail;
    }
}