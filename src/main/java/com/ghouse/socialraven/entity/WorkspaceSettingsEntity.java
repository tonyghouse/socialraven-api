package com.ghouse.socialraven.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "workspace_settings")
public class WorkspaceSettingsEntity {

    @Id
    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "default_tz", nullable = false)
    private String defaultTz;

    @Column(name = "brand_color", length = 10)
    private String brandColor;

    /** Reserved for future white-label support */
    @Column(name = "custom_domain")
    private String customDomain;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
