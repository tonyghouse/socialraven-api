package com.tonyghouse.socialraven.dto.workspace;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceLibraryItemResponse {
    private Long id;
    private String itemType;
    private String status;
    private String name;
    private String folderName;
    private String description;
    private String body;
    private String snippetTarget;
    private String postCollectionType;
    private List<String> tags;
    private List<WorkspaceLibraryMediaResponse> media;
    private Map<String, Object> platformConfigs;
    private String usageNotes;
    private String internalNotes;
    private OffsetDateTime expiresAt;
    private boolean expired;
    private boolean usable;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
