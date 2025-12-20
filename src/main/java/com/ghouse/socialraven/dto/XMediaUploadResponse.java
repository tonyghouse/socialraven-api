package com.ghouse.socialraven.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Response from Twitter media upload
 */
@Data
public class XMediaUploadResponse {
    
    @JsonProperty("media_id")
    private Long mediaId;
    
    @JsonProperty("media_id_string")
    private String mediaIdString;
    
    @JsonProperty("size")
    private Long size;
    
    @JsonProperty("expires_after_secs")
    private Long expiresAfterSecs;
    
    @JsonProperty("image")
    private ImageInfo image;
    
    @Data
    public static class ImageInfo {
        @JsonProperty("image_type")
        private String imageType;
        
        @JsonProperty("w")
        private Integer width;
        
        @JsonProperty("h")
        private Integer height;
    }
}



