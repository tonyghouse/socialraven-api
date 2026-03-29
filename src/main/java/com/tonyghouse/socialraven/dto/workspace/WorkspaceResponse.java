package com.tonyghouse.socialraven.dto.workspace;

import com.tonyghouse.socialraven.constant.WorkspaceRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceResponse {
    private String id;
    private String name;
    private String companyName;
    private String ownerUserId;
    private String logoS3Key;
    private WorkspaceRole role;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
