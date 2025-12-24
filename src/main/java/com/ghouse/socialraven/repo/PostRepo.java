package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.entity.PostCollectionEntity;
import com.ghouse.socialraven.entity.PostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepo extends JpaRepository<PostEntity, Long> {
    @Query("""
                select p
                from PostEntity p
                join fetch p.postCollection pc
                left join fetch pc.mediaFiles
                where p.id = :postId
            """)
    PostEntity findPostWithCollectionAndMedia(@Param("postId") Long postId);

    @Query("""
                select p
                from PostEntity p
                join p.postCollection pc
                where pc.userId = :userId
                  and p.postStatus = :postStatus
            """)
    Page<PostEntity> findByPostCollectionUserIdAndPostStatus(
            @Param("userId") String userId,
            @Param("postStatus") PostStatus postStatus,
            Pageable pageable
    );

    @Query("""
                select p
                from PostEntity p
                join p.postCollection pc
                where pc.userId = :userId
            """)
    Page<PostEntity> findByPostCollectionUserId(
            @Param("userId") String userId,
            Pageable pageable
    );


}

