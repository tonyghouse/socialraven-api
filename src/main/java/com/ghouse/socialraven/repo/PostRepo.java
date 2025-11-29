package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.entity.PostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepo extends JpaRepository<PostEntity, Long> {
    Page<PostEntity> findByUserIdOrderByScheduledTimeDesc(String userId, Pageable pageable);

    Page<PostEntity> findByUserIdAndPostStatusOrderByScheduledTimeDesc(String userId, PostStatus postStatus, Pageable pageable);

}

