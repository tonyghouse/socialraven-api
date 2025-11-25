package com.ghouse.socialraven.entity;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.constant.Provider;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "single_post")
public class SinglePostEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private PostStatus postStatus;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private Provider provider;

    @Column(name = "provider_user_ids", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> providerUserIds;


    @Column(length = 1000, nullable = false)
    private String title;

    @Column(length = 100000, nullable = false)
    private String description;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostMediaEntity> mediaFiles;

    private OffsetDateTime scheduledTime;

}
