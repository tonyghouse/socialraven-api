package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.PostAnalyticsSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface PostAnalyticsSnapshotRepo extends JpaRepository<PostAnalyticsSnapshotEntity, Long> {

    List<PostAnalyticsSnapshotEntity> findAllByPostId(Long postId);

    @Modifying
    @Query(value = "DELETE FROM socialraven.post_analytics_snapshots WHERE workspace_id = :workspaceId", nativeQuery = true)
    void deleteAllByWorkspaceId(@Param("workspaceId") String workspaceId);

    @Query(value = """
        SELECT s.* FROM socialraven.post_analytics_snapshots s
        JOIN socialraven.post p ON s.post_id = p.id
        JOIN socialraven.post_collection pc ON p.post_collection_id = pc.id
        WHERE pc.workspace_id = :workspaceId
          AND s.fetched_at >= :since
        ORDER BY s.fetched_at DESC
        """, nativeQuery = true)
    List<PostAnalyticsSnapshotEntity> findByWorkspaceIdSince(
            @Param("workspaceId") String workspaceId,
            @Param("since") OffsetDateTime since);

    @Query(value = """
        SELECT s.* FROM socialraven.post_analytics_snapshots s
        JOIN socialraven.post p ON s.post_id = p.id
        JOIN socialraven.post_collection pc ON p.post_collection_id = pc.id
        WHERE pc.workspace_id = :workspaceId
          AND s.snapshot_type = :snapshotType
          AND s.fetched_at >= :since
        ORDER BY (COALESCE(s.likes,0) + COALESCE(s.comments,0) + COALESCE(s.shares,0)) DESC
        """, nativeQuery = true)
    List<PostAnalyticsSnapshotEntity> findTopPostsByWorkspaceIdAndSnapshotType(
            @Param("workspaceId") String workspaceId,
            @Param("snapshotType") String snapshotType,
            @Param("since") OffsetDateTime since);
}
