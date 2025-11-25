package com.ghouse.socialraven.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghouse.socialraven.constant.Platform;
import lombok.Data;

@Data
public class ConnectedAccount {

    private String providerUserId;
    private Platform platform;
    private String username;
    private String profilePicLink;

    public static ConnectedAccount fromJson(String redisValue) {
        if (redisValue == null || redisValue.isBlank()) {
            return null;
        }

        try {
            return new ObjectMapper().readValue(redisValue, ConnectedAccount.class);
        } catch (Exception e) {
            // If parsing fails, consider it null
            return null;
        }
    }

    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert ConnectedAccount to JSON", e);
        }
    }
}
