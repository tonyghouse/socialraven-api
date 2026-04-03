package com.tonyghouse.socialraven.dto.workspace;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class UpsertWorkspaceLibraryItemRequest {
    private String itemType;
    private String status;
    private String name;
    private String folderName;
    private String description;
    private String body;
    private String snippetTarget;
    private String postCollectionType;
    private List<String> tags;
    private List<WorkspaceLibraryMediaRequest> media;
    private Map<String, Object> platformConfigs;
    private String usageNotes;
    private String internalNotes;
    private OffsetDateTime expiresAt;
}
