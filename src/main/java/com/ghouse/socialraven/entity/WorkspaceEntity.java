package com.ghouse.socialraven.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "workspace")
public class WorkspaceEntity {

    /** UUID for named workspaces; "personal_<clerkUserId>" for influencer personal workspaces */
    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    /** Shown as subtitle in the workspace switcher */
    @Column(name = "company_name")
    private String companyName;

    /** clerkUserId of the workspace owner — the billable party */
    @Column(name = "owner_user_id", nullable = false)
    private String ownerUserId;

    @Column(name = "logo_s3_key")
    private String logoS3Key;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
