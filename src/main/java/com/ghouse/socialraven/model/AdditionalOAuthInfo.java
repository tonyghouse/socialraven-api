package com.ghouse.socialraven.model;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class AdditionalOAuthInfo {

    private String xRefreshToken;


    private String youtubeRefreshToken;

    private String facebookPageId;

    private String instagramBusinessId;


}
