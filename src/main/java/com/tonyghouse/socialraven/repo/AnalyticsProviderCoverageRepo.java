package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.AnalyticsProviderCoverageEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsProviderCoverageRepo extends JpaRepository<AnalyticsProviderCoverageEntity, Long> {

    List<AnalyticsProviderCoverageEntity> findAllByWorkspaceIdOrderByProviderAsc(String workspaceId);

    Optional<AnalyticsProviderCoverageEntity> findByWorkspaceIdAndProvider(String workspaceId, Provider provider);
}
