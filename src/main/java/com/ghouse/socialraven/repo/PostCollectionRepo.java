package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.PostCollectionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}

