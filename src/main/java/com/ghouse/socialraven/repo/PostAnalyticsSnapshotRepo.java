package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.PostAnalyticsSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface PostAnalyticsSnapshotRepo extends JpaRepository<PostAnalyticsSnapshotEntity, Long> {

    List<PostAnalyticsSnapshotEntity> findAllByPostId(Long postId);

    @Query(value = """
        SELECT s.* FROM socialraven.post_analytics_snapshots s
        JOIN socialraven.post p ON s.post_id = p.id
        JOIN socialraven.post_collection pc ON p.post_collection_id = pc.id
        WHERE pc.user_id = :userId
          AND s.fetched_at >= :since
        ORDER BY s.fetched_at DESC
        """, nativeQuery = true)
    List<PostAnalyticsSnapshotEntity> findByUserIdSince(
            @Param("userId") String userId,
            @Param("since") OffsetDateTime since);

    @Query(value = """
        SELECT s.* FROM socialraven.post_analytics_snapshots s
        JOIN socialraven.post p ON s.post_id = p.id
        JOIN socialraven.post_collection pc ON p.post_collection_id = pc.id
        WHERE pc.user_id = :userId
          AND s.snapshot_type = :snapshotType
          AND s.fetched_at >= :since
        ORDER BY (COALESCE(s.likes,0) + COALESCE(s.comments,0) + COALESCE(s.shares,0)) DESC
        """, nativeQuery = true)
    List<PostAnalyticsSnapshotEntity> findTopPostsByUserIdAndSnapshotType(
            @Param("userId") String userId,
            @Param("snapshotType") String snapshotType,
            @Param("since") OffsetDateTime since);
}
