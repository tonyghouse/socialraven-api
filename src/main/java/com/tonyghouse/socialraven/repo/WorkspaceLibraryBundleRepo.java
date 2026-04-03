package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.WorkspaceLibraryBundleEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceLibraryBundleRepo extends JpaRepository<WorkspaceLibraryBundleEntity, Long> {
    List<WorkspaceLibraryBundleEntity> findAllByWorkspaceIdOrderByUpdatedAtDesc(String workspaceId);

    Optional<WorkspaceLibraryBundleEntity> findByIdAndWorkspaceId(Long id, String workspaceId);
}
