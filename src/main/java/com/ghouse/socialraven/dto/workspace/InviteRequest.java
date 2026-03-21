package com.ghouse.socialraven.dto.workspace;

import com.ghouse.socialraven.constant.WorkspaceRole;
import lombok.Data;

import java.util.List;

@Data
public class InviteRequest {
    /** Email address to invite. */
    private String email;

    /** Role to assign in each selected workspace. */
    private WorkspaceRole role;

    /**
     * Workspace IDs to invite the user to (Option A — multi-workspace invite).
     * At least one is required.
     */
    private List<String> workspaceIds;
}
