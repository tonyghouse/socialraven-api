package com.tonyghouse.socialraven.dto.clientconnect;

import lombok.Data;

@Data
public class PublicClientConnectionCallbackRequest {
    private String code;
    private String accessToken;
    private String accessTokenSecret;
    private String actorDisplayName;
    private String actorEmail;
}
