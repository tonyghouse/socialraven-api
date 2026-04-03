package com.tonyghouse.socialraven.dto.workspace;

import lombok.Data;

@Data
public class WorkspaceLibraryMediaRequest {
    private String fileName;
    private String mimeType;
    private String fileKey;
    private Long size;
}
