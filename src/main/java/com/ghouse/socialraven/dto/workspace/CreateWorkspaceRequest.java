package com.ghouse.socialraven.dto.workspace;

import lombok.Data;

@Data
public class CreateWorkspaceRequest {
    private String name;
    private String companyName;
    private String logoS3Key;
}
