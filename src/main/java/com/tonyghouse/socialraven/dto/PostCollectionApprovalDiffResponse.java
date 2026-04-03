package com.tonyghouse.socialraven.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostCollectionApprovalDiffResponse {
    private Long approvedVersionId;
    private Integer approvedVersionNumber;
    private Long currentVersionId;
    private Integer currentVersionNumber;
    private boolean hasChanges;
    private List<PostCollectionApprovalDiffItemResponse> changes;
}
