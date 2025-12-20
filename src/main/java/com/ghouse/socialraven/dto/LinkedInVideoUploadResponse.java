package com.ghouse.socialraven.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Response from LinkedIn video upload initialization
 */
@Data
public class LinkedInVideoUploadResponse {
    private ValueInfo value;

    @Data
    public static class ValueInfo {
        private String video;        // Video URN
        private String uploadUrl;    // URL to upload video bytes
    }
}



