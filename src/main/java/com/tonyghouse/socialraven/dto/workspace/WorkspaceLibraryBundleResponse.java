package com.tonyghouse.socialraven.dto.workspace;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceLibraryBundleResponse {
    private Long id;
    private String name;
    private String description;
    private String campaignLabel;
    private List<Long> itemIds;
    private List<WorkspaceLibraryItemResponse> items;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
