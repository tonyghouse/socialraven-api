package com.ghouse.socialraven.entity;


import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "post_media")
public class PostMediaEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private String fileUrl;

    @Column(nullable = false)
    private String fileKey;

    @Column(nullable = false)
    private Long size;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_collection_id", nullable = false)
    private PostCollectionEntity postCollection;

}
