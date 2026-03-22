package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceRepo extends JpaRepository<WorkspaceEntity, String> {
    List<WorkspaceEntity> findAllByOwnerUserId(String ownerUserId);

    /** Active (not soft-deleted) workspaces owned by a user — used for plan-gate checks. */
    List<WorkspaceEntity> findAllByOwnerUserIdAndDeletedAtIsNull(String ownerUserId);

    /** Active workspace by id — returns empty if soft-deleted. */
    Optional<WorkspaceEntity> findByIdAndDeletedAtIsNull(String id);

    /** Workspaces whose soft-delete retention window has expired and are ready for hard-delete. */
    List<WorkspaceEntity> findAllByDeletedAtIsNotNullAndDeletedAtBefore(OffsetDateTime cutoff);
}
