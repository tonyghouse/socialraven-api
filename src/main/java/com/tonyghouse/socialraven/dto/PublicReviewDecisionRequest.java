package com.tonyghouse.socialraven.dto;

import lombok.Data;

@Data
public class PublicReviewDecisionRequest {
    private String reviewerName;
    private String reviewerEmail;
    private String note;
}
