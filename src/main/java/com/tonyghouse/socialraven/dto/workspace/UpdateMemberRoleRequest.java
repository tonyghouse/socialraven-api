package com.tonyghouse.socialraven.dto.workspace;

import com.tonyghouse.socialraven.constant.WorkspaceRole;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {
    private WorkspaceRole role;
}
