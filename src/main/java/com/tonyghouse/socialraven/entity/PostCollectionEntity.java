package com.tonyghouse.socialraven.entity;

import com.tonyghouse.socialraven.constant.PostCollectionType;
import com.tonyghouse.socialraven.constant.RecoveryState;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "post_collection")
@Data
public class PostCollectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Attribution only — "Scheduled by Sara". Never used for data scoping. */
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "workspace_id")
    private String workspaceId;

    @Column(length = 100000, nullable = false)
    private String description;

    @Column(name = "is_draft", nullable = false)
    private boolean draft;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private PostCollectionType postCollectionType;

    @OneToMany(
            mappedBy = "postCollection",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PostEntity> posts;

    @OneToMany(
            mappedBy = "postCollection",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PostMediaEntity> mediaFiles;

    private OffsetDateTime scheduledTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String platformConfigs;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_state", nullable = false, length = 50)
    private RecoveryState failureState = RecoveryState.NONE;

    private OffsetDateTime failureDetectedAt;

    @Column(length = 2000)
    private String failureReasonSummary;

    @Column(nullable = false)
    private int notificationAttemptCount;

    private OffsetDateTime lastNotificationSentAt;
    private OffsetDateTime nextNotificationAt;
    private OffsetDateTime notificationStoppedAt;

    private Long recoveryCollectionId;
    private Long recoverySourceCollectionId;

    private OffsetDateTime handledAt;

    @Column(length = 255)
    private String handledBy;

    private OffsetDateTime adminEscalatedAt;
}
