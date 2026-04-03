package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.WorkspaceLibraryItemEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceLibraryItemRepo extends JpaRepository<WorkspaceLibraryItemEntity, Long> {
    List<WorkspaceLibraryItemEntity> findAllByWorkspaceIdOrderByUpdatedAtDesc(String workspaceId);

    Optional<WorkspaceLibraryItemEntity> findByIdAndWorkspaceId(Long id, String workspaceId);
}
