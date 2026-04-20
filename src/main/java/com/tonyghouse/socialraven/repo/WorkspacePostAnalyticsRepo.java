package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.WorkspacePostAnalyticsEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspacePostAnalyticsRepo extends JpaRepository<WorkspacePostAnalyticsEntity, Long> {

    List<WorkspacePostAnalyticsEntity> findAllByWorkspaceId(String workspaceId);

    long countByWorkspaceId(String workspaceId);

    long countByWorkspaceIdAndPostIdIn(String workspaceId, Collection<Long> postIds);

    long countByWorkspaceIdAndProvider(String workspaceId, Provider provider);

    List<WorkspacePostAnalyticsEntity> findAllByPostIdIn(Collection<Long> postIds);

    Optional<WorkspacePostAnalyticsEntity> findByPostId(Long postId);
}
