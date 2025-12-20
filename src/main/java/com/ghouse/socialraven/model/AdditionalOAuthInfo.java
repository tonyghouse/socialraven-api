package com.ghouse.socialraven.model;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class AdditionalOAuthInfo {

    private String xTokenSecret;        // OAuth 1.0a token secret (REQUIRED)

    private String youtubeRefreshToken;

}
