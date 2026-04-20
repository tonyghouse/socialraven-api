package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.AnalyticsRecommendationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsRecommendationRepo extends JpaRepository<AnalyticsRecommendationEntity, Long> {

    List<AnalyticsRecommendationEntity> findAllByWorkspaceIdAndSliceKeyOrderByExpectedImpactScoreDesc(
            String workspaceId,
            String sliceKey
    );

    Optional<AnalyticsRecommendationEntity> findByIdAndWorkspaceId(Long id, String workspaceId);
}
