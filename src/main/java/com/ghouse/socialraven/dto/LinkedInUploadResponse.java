package com.ghouse.socialraven.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class LinkedInUploadResponse {
    private Value value;

    @Data
    public static class Value {
        private String asset;
        
        // FIXED: LinkedIn returns uploadMechanism as an OBJECT, not a List
        private Map<String, MediaUploadInfo> uploadMechanism;
    }

    @Data
    public static class MediaUploadInfo {
        @JsonProperty("com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest")
        private MediaUploadHttpRequest mediaUploadHttpRequest;
    }

    @Data
    public static class MediaUploadHttpRequest {
        private String uploadUrl;
        private Map<String, String> headers;
    }
}