package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Note: fromDate / toDate are NEVER null — the service always passes sentinel values
 * (year 2000 / year 2100) when no date range is selected, so PostgreSQL can always
 * infer the parameter type.  IS NULL guards on OffsetDateTime params cause
 * "could not determine data type of parameter" errors with the PostgreSQL JDBC driver.
 *
 * All queries scope by workspaceId (the data boundary). createdBy is attribution only.
 */
@Repository
public interface PostCollectionRepo extends JpaRepository<PostCollectionEntity, Long> {

    Page<PostCollectionEntity> findByWorkspaceIdOrderByScheduledTimeDesc(String workspaceId, Pageable pageable);

    Optional<PostCollectionEntity> findByIdAndWorkspaceId(Long id, String workspaceId);

    /** Used by WorkspaceDeletionScheduler to cascade-delete posts and media via JPA. */
    List<PostCollectionEntity> findAllByWorkspaceId(String workspaceId);

    /**
     * Counts non-draft post-collections for a workspace scheduled on or after startOfMonth.
     * Used for monthly usage quota calculation.
     */
    @Query("""
            SELECT COUNT(pc)
            FROM PostCollectionEntity pc
            WHERE pc.workspaceId = :workspaceId
              AND pc.draft = false
              AND pc.scheduledTime >= :startOfMonth
            """)
    long countNonDraftPostsFromMonth(
            @Param("workspaceId") String workspaceId,
            @Param("startOfMonth") OffsetDateTime startOfMonth
    );

    // DRAFT — with optional search (no date range; drafts have no scheduledTime)
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND c.draft = true " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search)",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND c.draft = true " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search)")
    Page<PostCollectionEntity> findDraftCollections(
            @Param("workspaceId") String workspaceId,
            @Param("search") String search,
            Pageable pageable);

    // DRAFT — with platform filter
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND c.draft = true " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND EXISTS (SELECT p FROM PostEntity p WHERE p.postCollection = c AND cast(p.provider as String) = :platform)",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND c.draft = true " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND EXISTS (SELECT p FROM PostEntity p WHERE p.postCollection = c AND cast(p.provider as String) = :platform)")
    Page<PostCollectionEntity> findDraftCollectionsByPlatform(
            @Param("workspaceId") String workspaceId,
            @Param("search") String search,
            @Param("platform") String platform,
            Pageable pageable);

    // ──────────────────────────────────────────────────────────────────────────────
    // SCHEDULED — no platform filter
    // ──────────────────────────────────────────────────────────────────────────────
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus != com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus != com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate")
    Page<PostCollectionEntity> findScheduledCollections(
            @Param("workspaceId") String workspaceId,
            @Param("search") String search,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable);

    // SCHEDULED — with platform filter
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus != com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND EXISTS (SELECT p3 FROM PostEntity p3 WHERE p3.postCollection = c AND cast(p3.provider as String) = :platform) " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus != com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND EXISTS (SELECT p3 FROM PostEntity p3 WHERE p3.postCollection = c AND cast(p3.provider as String) = :platform) " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate")
    Page<PostCollectionEntity> findScheduledCollectionsByPlatform(
            @Param("workspaceId") String workspaceId,
            @Param("search") String search,
            @Param("platform") String platform,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable);

    // SCHEDULED — with account filter, no platform
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c JOIN c.posts p WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus != com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND p.providerUserId IN :providerUserIds " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c JOIN c.posts p WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus != com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND p.providerUserId IN :providerUserIds " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate")
    Page<PostCollectionEntity> findScheduledCollectionsWithAccounts(
            @Param("workspaceId") String workspaceId,
            @Param("search") String search,
            @Param("providerUserIds") List<String> providerUserIds,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable);

    // SCHEDULED — with account filter + platform
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c JOIN c.posts p WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus != com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND EXISTS (SELECT p3 FROM PostEntity p3 WHERE p3.postCollection = c AND cast(p3.provider as String) = :platform) " +
           "AND p.providerUserId IN :providerUserIds " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c JOIN c.posts p WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus != com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND EXISTS (SELECT p3 FROM PostEntity p3 WHERE p3.postCollection = c AND cast(p3.provider as String) = :platform) " +
           "AND p.providerUserId IN :providerUserIds " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate")
    Page<PostCollectionEntity> findScheduledCollectionsWithAccountsAndPlatform(
            @Param("workspaceId") String workspaceId,
            @Param("search") String search,
            @Param("platform") String platform,
            @Param("providerUserIds") List<String> providerUserIds,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable);

    // ──────────────────────────────────────────────────────────────────────────────
    // PUBLISHED — no platform filter
    // ──────────────────────────────────────────────────────────────────────────────
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p FROM PostEntity p WHERE p.postCollection = c " +
           "AND p.postStatus = com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus = com.tonyghouse.socialraven.constant.PostStatus.PUBLISHED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p FROM PostEntity p WHERE p.postCollection = c " +
           "AND p.postStatus = com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus = com.tonyghouse.socialraven.constant.PostStatus.PUBLISHED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate")
    Page<PostCollectionEntity> findPublishedCollections(
            @Param("workspaceId") String workspaceId,
            @Param("search") String search,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable);

    // PUBLISHED — with platform filter
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p FROM PostEntity p WHERE p.postCollection = c " +
           "AND p.postStatus = com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus = com.tonyghouse.socialraven.constant.PostStatus.PUBLISHED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND EXISTS (SELECT p3 FROM PostEntity p3 WHERE p3.postCollection = c AND cast(p3.provider as String) = :platform) " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p FROM PostEntity p WHERE p.postCollection = c " +
           "AND p.postStatus = com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus = com.tonyghouse.socialraven.constant.PostStatus.PUBLISHED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND EXISTS (SELECT p3 FROM PostEntity p3 WHERE p3.postCollection = c AND cast(p3.provider as String) = :platform) " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate")
    Page<PostCollectionEntity> findPublishedCollectionsByPlatform(
            @Param("workspaceId") String workspaceId,
            @Param("search") String search,
            @Param("platform") String platform,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable);

    // PUBLISHED — with account filter, no platform
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c JOIN c.posts p WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus = com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND EXISTS (SELECT p3 FROM PostEntity p3 WHERE p3.postCollection = c " +
           "AND p3.postStatus = com.tonyghouse.socialraven.constant.PostStatus.PUBLISHED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND p.providerUserId IN :providerUserIds " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c JOIN c.posts p WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus = com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND EXISTS (SELECT p3 FROM PostEntity p3 WHERE p3.postCollection = c " +
           "AND p3.postStatus = com.tonyghouse.socialraven.constant.PostStatus.PUBLISHED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND p.providerUserId IN :providerUserIds " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate")
    Page<PostCollectionEntity> findPublishedCollectionsWithAccounts(
            @Param("workspaceId") String workspaceId,
            @Param("search") String search,
            @Param("providerUserIds") List<String> providerUserIds,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable);

    // PUBLISHED — with account filter + platform
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c JOIN c.posts p WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus = com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND EXISTS (SELECT p4 FROM PostEntity p4 WHERE p4.postCollection = c " +
           "AND p4.postStatus = com.tonyghouse.socialraven.constant.PostStatus.PUBLISHED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND EXISTS (SELECT p3 FROM PostEntity p3 WHERE p3.postCollection = c AND cast(p3.provider as String) = :platform) " +
           "AND p.providerUserId IN :providerUserIds " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c JOIN c.posts p WHERE c.workspaceId = :workspaceId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus = com.tonyghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND EXISTS (SELECT p4 FROM PostEntity p4 WHERE p4.postCollection = c " +
           "AND p4.postStatus = com.tonyghouse.socialraven.constant.PostStatus.PUBLISHED) " +
           "AND (:search IS NULL OR LOWER(c.description) LIKE :search) " +
           "AND EXISTS (SELECT p3 FROM PostEntity p3 WHERE p3.postCollection = c AND cast(p3.provider as String) = :platform) " +
           "AND p.providerUserId IN :providerUserIds " +
           "AND c.scheduledTime >= :fromDate AND c.scheduledTime <= :toDate")
    Page<PostCollectionEntity> findPublishedCollectionsWithAccountsAndPlatform(
            @Param("workspaceId") String workspaceId,
            @Param("search") String search,
            @Param("platform") String platform,
            @Param("providerUserIds") List<String> providerUserIds,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable);
}
