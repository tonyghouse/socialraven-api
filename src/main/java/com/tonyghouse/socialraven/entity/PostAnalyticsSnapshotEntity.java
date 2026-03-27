package com.tonyghouse.socialraven.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Table(name = "post_analytics_snapshots")
@Data
public class PostAnalyticsSnapshotEntity {

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

    private Long impressions;

    private Long reach;

    private Long likes;

    private Long comments;

    private Long shares;

    private Long saves;

    private Long clicks;

    @Column(name = "video_views")
    private Long videoViews;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;
}
