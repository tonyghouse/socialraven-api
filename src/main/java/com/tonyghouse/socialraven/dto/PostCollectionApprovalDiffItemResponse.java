package com.tonyghouse.socialraven.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostCollectionApprovalDiffItemResponse {
    private String field;
    private String label;
    private String valueType;
    private String beforeValue;
    private String afterValue;
}
