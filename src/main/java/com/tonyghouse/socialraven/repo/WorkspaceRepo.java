package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceRepo extends JpaRepository<WorkspaceEntity, String> {
    List<WorkspaceEntity> findAllByCompanyId(String companyId);
    List<WorkspaceEntity> findAllByCompanyIdAndDeletedAtIsNull(String companyId);

    /** Active workspace by id — returns empty if soft-deleted. */
    Optional<WorkspaceEntity> findByIdAndDeletedAtIsNull(String id);

    /** Workspaces whose soft-delete retention window has expired and are ready for hard-delete. */
    List<WorkspaceEntity> findAllByDeletedAtIsNotNullAndDeletedAtBefore(OffsetDateTime cutoff);
}
