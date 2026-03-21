package com.ghouse.socialraven.dto.workspace;

import com.ghouse.socialraven.constant.WorkspaceRole;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {
    private WorkspaceRole role;
}
