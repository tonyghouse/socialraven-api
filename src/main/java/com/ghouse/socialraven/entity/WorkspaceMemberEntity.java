package com.ghouse.socialraven.entity;

import com.ghouse.socialraven.constant.WorkspaceRole;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(
    name = "workspace_member",
    uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "user_id"})
)
public class WorkspaceMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 50, nullable = false)
    private WorkspaceRole role;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;
}
