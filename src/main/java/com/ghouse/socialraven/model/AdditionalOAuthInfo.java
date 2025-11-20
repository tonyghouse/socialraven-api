package com.ghouse.socialraven.model;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class AdditionalOAuthInfo {

    private String linkedInUserId;

    private String xUserId;
    private String xAccessSecret;

    private String instagramUserId;

    private String youtubeUserId;
    private String youtubeRefreshToken;

    private String facebookUserId;

}
