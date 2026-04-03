package com.tonyghouse.socialraven.dto.workspace;

import java.util.List;
import lombok.Data;

@Data
public class UpsertWorkspaceLibraryBundleRequest {
    private String name;
    private String description;
    private String campaignLabel;
    private List<Long> itemIds;
}
