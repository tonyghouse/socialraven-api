package com.tonyghouse.socialraven.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicReviewChannelResponse {
    private String platform;
    private String username;
    private String profilePicLink;
}
