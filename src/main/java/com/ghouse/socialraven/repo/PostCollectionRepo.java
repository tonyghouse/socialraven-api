package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.PostCollectionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostCollectionRepo extends JpaRepository<PostCollectionEntity, Long> {

    Page<PostCollectionEntity> findByUserIdOrderByScheduledTimeDesc(String userId, Pageable pageable);
}

