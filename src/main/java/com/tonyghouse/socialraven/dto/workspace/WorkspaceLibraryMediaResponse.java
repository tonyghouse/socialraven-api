package com.tonyghouse.socialraven.dto.workspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceLibraryMediaResponse {
    private String fileName;
    private String mimeType;
    private String fileKey;
    private Long size;
    private String fileUrl;
}
