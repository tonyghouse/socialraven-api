package com.tonyghouse.socialraven.dto.workspace;

import lombok.Data;

@Data
public class CreateWorkspaceRequest {
    private String name;
    private String companyId;
    private String companyName;
    private String logoS3Key;
}
