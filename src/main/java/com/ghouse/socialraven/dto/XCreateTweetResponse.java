package com.ghouse.socialraven.dto;

import lombok.Data;

/**
 * Response from creating a tweet
 */
@Data
public class XCreateTweetResponse {
    private TweetData data;
    
    @Data
    public static class TweetData {
        private String id;
        private String text;
    }
}