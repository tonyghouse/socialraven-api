package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.AnalyticsJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalyticsJobRepo extends JpaRepository<AnalyticsJobEntity, Long> {
    List<AnalyticsJobEntity> findAllByPostId(Long postId);
}
