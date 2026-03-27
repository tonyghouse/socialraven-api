package com.tonyghouse.socialraven.dto.workspace;

import lombok.Data;

@Data
public class UpdateWorkspaceRequest {
    private String name;
    private String companyName;
    private String logoS3Key;
}
