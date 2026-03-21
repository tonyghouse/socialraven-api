package com.ghouse.socialraven.entity;

import com.ghouse.socialraven.constant.WorkspaceRole;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "workspace_invitation")
public class WorkspaceInvitationEntity {

    @Id
    @Column(name = "token", nullable = false, updatable = false)
    private UUID token;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "invited_email", nullable = false)
    private String invitedEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 50, nullable = false)
    private WorkspaceRole role;

    @Column(name = "invited_by", nullable = false)
    private String invitedBy;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
