package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.WorkspaceClientConnectionSessionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceClientConnectionSessionRepo extends JpaRepository<WorkspaceClientConnectionSessionEntity, String> {
    List<WorkspaceClientConnectionSessionEntity> findAllByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);

    Optional<WorkspaceClientConnectionSessionEntity> findByIdAndWorkspaceId(String id, String workspaceId);
}
