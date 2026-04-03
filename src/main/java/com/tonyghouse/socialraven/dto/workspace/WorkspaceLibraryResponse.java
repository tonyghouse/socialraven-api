package com.tonyghouse.socialraven.dto.workspace;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceLibraryResponse {
    private List<WorkspaceLibraryItemResponse> items;
    private List<WorkspaceLibraryBundleResponse> bundles;
}
