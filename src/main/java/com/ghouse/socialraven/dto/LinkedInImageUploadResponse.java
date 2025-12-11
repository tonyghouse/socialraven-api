package com.ghouse.socialraven.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LinkedInImageUploadResponse {
    
    @JsonProperty("value")
    private Value value;
    
    @Data
    public static class Value {
        @JsonProperty("image")
        private String image; // This is the image URN
        
        @JsonProperty("uploadUrl")
        private String uploadUrl; // Direct upload URL
    }
}