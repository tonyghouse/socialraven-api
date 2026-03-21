package com.ghouse.socialraven.dto.workspace;

import com.ghouse.socialraven.constant.WorkspaceRole;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class InvitationResponse {
    private UUID token;
    private String workspaceId;
    private String invitedEmail;
    private WorkspaceRole role;
    private String invitedBy;
    private OffsetDateTime expiresAt;
    private OffsetDateTime acceptedAt;
    private OffsetDateTime createdAt;
}
