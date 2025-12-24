package com.ghouse.socialraven.entity;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.constant.PostType;
import com.ghouse.socialraven.constant.Provider;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Table(name = "post")
@Data
public class PostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Provider provider;

    @Column(length = 1000, nullable = false)
    private String providerUserId;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private PostStatus postStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private PostType postType;

    private OffsetDateTime scheduledTime;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_collection_id", nullable = false)
    private PostCollectionEntity postCollection;

    private OffsetDateTime createdAt;
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

