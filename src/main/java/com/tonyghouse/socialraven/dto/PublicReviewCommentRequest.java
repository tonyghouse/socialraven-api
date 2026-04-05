package com.tonyghouse.socialraven.dto;

import lombok.Data;

@Data
public class PublicReviewCommentRequest {
    private String reviewerName;
    private String reviewerEmail;
    private String body;
    private Integer anchorStart;
    private Integer anchorEnd;
    private String anchorText;
    private Long mediaId;
    private Double mediaMarkerX;
    private Double mediaMarkerY;
}
