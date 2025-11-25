// com.ghouse.socialraven.dto.XOAuthCallbackRequest
package com.ghouse.socialraven.dto;

import lombok.Data;

@Data
public class XOAuthCallbackRequest {
    private String code;
    private String codeVerifier;
    private String appUserId; // from Clerk / frontend
}
