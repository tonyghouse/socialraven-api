package com.ghouse.socialraven.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Request for creating a tweet with media
 */
@Data
public class XCreateTweetRequest {
    private String text;
    private MediaInfo media;
    
    @Data
    public static class MediaInfo {
        @JsonProperty("media_ids")
        private List<String> mediaIds;
    }
}