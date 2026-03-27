package com.tonyghouse.socialraven.dto.workspace;

import com.tonyghouse.socialraven.constant.WorkspaceRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
public class MemberResponse {
    private String userId;
    private WorkspaceRole role;
    private OffsetDateTime joinedAt;
    private String firstName;
    private String lastName;
    private String email;
}
