package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.entity.WorkspaceUserCapabilityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceUserCapabilityRepo extends JpaRepository<WorkspaceUserCapabilityEntity, Long> {
    List<WorkspaceUserCapabilityEntity> findAllByWorkspaceIdAndUserId(String workspaceId, String userId);
    List<WorkspaceUserCapabilityEntity> findAllByWorkspaceIdAndCapability(String workspaceId, WorkspaceCapability capability);
    List<WorkspaceUserCapabilityEntity> findAllByWorkspaceIdInAndCapability(List<String> workspaceIds, WorkspaceCapability capability);
    Optional<WorkspaceUserCapabilityEntity> findByWorkspaceIdAndUserIdAndCapability(
            String workspaceId,
            String userId,
            WorkspaceCapability capability
    );
    void deleteAllByWorkspaceIdAndUserId(String workspaceId, String userId);
}
