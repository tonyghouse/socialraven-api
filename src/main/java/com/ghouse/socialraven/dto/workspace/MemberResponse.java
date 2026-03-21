package com.ghouse.socialraven.dto.workspace;

import com.ghouse.socialraven.constant.WorkspaceRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
public class MemberResponse {
    private String userId;
    private WorkspaceRole role;
    private OffsetDateTime joinedAt;
}
