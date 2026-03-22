package com.ghouse.socialraven.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Table(name = "analytics_jobs")
@Data
public class AnalyticsJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_post_id", nullable = false)
    private String providerPostId;

    @Column(name = "snapshot_type", nullable = false, length = 10)
    private String snapshotType;

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    @Column(nullable = false, length = 15)
    private String status = "PENDING";

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_attempted_at")
    private OffsetDateTime lastAttemptedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
