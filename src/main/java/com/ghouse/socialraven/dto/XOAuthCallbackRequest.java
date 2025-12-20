package com.ghouse.socialraven.dto;

import lombok.Data;

@Data
public class XOAuthCallbackRequest {
    // OAuth 1.0a fields (received from frontend)
    private String accessToken;
    private String accessTokenSecret;
    private String userId;          // X user ID
    private String screenName;      // X username (@handle)
    
    // Deprecated OAuth 2.0 fields (remove if not used elsewhere)
    // private String code;
    // private String codeVerifier;
}