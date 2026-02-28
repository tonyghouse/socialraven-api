package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.PostCollectionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostCollectionRepo extends JpaRepository<PostCollectionEntity, Long> {

    Page<PostCollectionEntity> findByUserIdOrderByScheduledTimeDesc(String userId, Pageable pageable);

    // Collections where ALL posts are still SCHEDULED (no post has been processed yet)
    @Query("SELECT c FROM PostCollectionEntity c WHERE c.userId = :userId " +
           "AND NOT EXISTS (SELECT p FROM PostEntity p WHERE p.postCollection = c " +
           "AND p.postStatus != com.ghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "ORDER BY c.scheduledTime DESC")
    Page<PostCollectionEntity> findScheduledCollectionsByUserId(@Param("userId") String userId, Pageable pageable);

    // Collections where NO post is still SCHEDULED (all have been processed)
    @Query("SELECT c FROM PostCollectionEntity c WHERE c.userId = :userId " +
           "AND NOT EXISTS (SELECT p FROM PostEntity p WHERE p.postCollection = c " +
           "AND p.postStatus = com.ghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "ORDER BY c.scheduledTime DESC")
    Page<PostCollectionEntity> findPublishedCollectionsByUserId(@Param("userId") String userId, Pageable pageable);

    // --- Search: Scheduled (title/description, no account filter) ---
    @Query("SELECT c FROM PostCollectionEntity c WHERE c.userId = :userId " +
           "AND NOT EXISTS (SELECT p FROM PostEntity p WHERE p.postCollection = c " +
           "AND p.postStatus != com.ghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.title) LIKE :search OR LOWER(c.description) LIKE :search) " +
           "ORDER BY c.scheduledTime DESC")
    Page<PostCollectionEntity> searchScheduledCollections(
            @Param("userId") String userId,
            @Param("search") String search,
            Pageable pageable);

    // --- Search: Scheduled + account filter ---
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c JOIN c.posts p WHERE c.userId = :userId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus != com.ghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.title) LIKE :search OR LOWER(c.description) LIKE :search) " +
           "AND p.providerUserId IN :providerUserIds " +
           "ORDER BY c.scheduledTime DESC",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c JOIN c.posts p WHERE c.userId = :userId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus != com.ghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.title) LIKE :search OR LOWER(c.description) LIKE :search) " +
           "AND p.providerUserId IN :providerUserIds")
    Page<PostCollectionEntity> searchScheduledCollectionsWithAccounts(
            @Param("userId") String userId,
            @Param("search") String search,
            @Param("providerUserIds") List<String> providerUserIds,
            Pageable pageable);

    // --- Search: Published (title/description, no account filter) ---
    @Query("SELECT c FROM PostCollectionEntity c WHERE c.userId = :userId " +
           "AND NOT EXISTS (SELECT p FROM PostEntity p WHERE p.postCollection = c " +
           "AND p.postStatus = com.ghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.title) LIKE :search OR LOWER(c.description) LIKE :search) " +
           "ORDER BY c.scheduledTime DESC")
    Page<PostCollectionEntity> searchPublishedCollections(
            @Param("userId") String userId,
            @Param("search") String search,
            Pageable pageable);

    // --- Search: Published + account filter ---
    @Query(value = "SELECT DISTINCT c FROM PostCollectionEntity c JOIN c.posts p WHERE c.userId = :userId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus = com.ghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.title) LIKE :search OR LOWER(c.description) LIKE :search) " +
           "AND p.providerUserId IN :providerUserIds " +
           "ORDER BY c.scheduledTime DESC",
           countQuery = "SELECT COUNT(DISTINCT c) FROM PostCollectionEntity c JOIN c.posts p WHERE c.userId = :userId " +
           "AND NOT EXISTS (SELECT p2 FROM PostEntity p2 WHERE p2.postCollection = c " +
           "AND p2.postStatus = com.ghouse.socialraven.constant.PostStatus.SCHEDULED) " +
           "AND (:search IS NULL OR LOWER(c.title) LIKE :search OR LOWER(c.description) LIKE :search) " +
           "AND p.providerUserId IN :providerUserIds")
    Page<PostCollectionEntity> searchPublishedCollectionsWithAccounts(
            @Param("userId") String userId,
            @Param("search") String search,
            @Param("providerUserIds") List<String> providerUserIds,
            Pageable pageable);
}

