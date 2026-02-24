package com.ghouse.socialraven.entity;

import com.ghouse.socialraven.constant.PostCollectionStatus;
import com.ghouse.socialraven.constant.PostCollectionType;
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

    @Column(nullable = false)
    private String userId;

    @Column(length = 1000, nullable = false)
    private String title;

    @Column(length = 100000, nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private PostCollectionStatus postCollectionStatus; //TODO-REMOVE

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
}
