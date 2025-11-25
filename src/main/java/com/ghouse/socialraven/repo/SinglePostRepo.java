package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.entity.SinglePostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SinglePostRepo extends JpaRepository<SinglePostEntity, Long> {
    Page<SinglePostEntity> findByUserIdOrderByScheduledTimeDesc(String userId, Pageable pageable);

    Page<SinglePostEntity> findByUserIdAndPostStatusOrderByScheduledTimeDesc(String userId, PostStatus postStatus, Pageable pageable);

}

