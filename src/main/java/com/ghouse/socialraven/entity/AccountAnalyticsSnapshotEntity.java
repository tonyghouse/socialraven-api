package com.ghouse.socialraven.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "account_analytics_snapshots")
@Data
public class AccountAnalyticsSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    private Long followers;

    private Long following;

    @Column(name = "impressions_day")
    private Long impressionsDay;

    @Column(name = "reach_day")
    private Long reachDay;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;
}
