package com.tonyghouse.socialraven.dto;

import lombok.Data;

@Data
public class PublicReviewCommentRequest {
    private String reviewerName;
    private String reviewerEmail;
    private String body;
}
