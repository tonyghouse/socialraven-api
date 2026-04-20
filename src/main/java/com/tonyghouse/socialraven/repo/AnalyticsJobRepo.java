package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.constant.AnalyticsJobStatus;
import com.tonyghouse.socialraven.constant.AnalyticsJobTrigger;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.AnalyticsJobEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsJobRepo extends JpaRepository<AnalyticsJobEntity, Long> {

    List<AnalyticsJobEntity> findAllByPostId(Long postId);

    Optional<AnalyticsJobEntity> findByDedupeKey(String dedupeKey);

    Optional<AnalyticsJobEntity> findTopByWorkspaceIdAndProviderAndTriggerTypeOrderByCreatedAtDesc(
            String workspaceId,
            Provider provider,
            AnalyticsJobTrigger triggerType
    );

    long countByWorkspaceIdAndStatus(String workspaceId, AnalyticsJobStatus status);

    void deleteAllByWorkspaceId(String workspaceId);
}
