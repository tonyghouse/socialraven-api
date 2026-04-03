package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.WorkspaceClientReportLinkEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceClientReportLinkRepo extends JpaRepository<WorkspaceClientReportLinkEntity, String> {
    List<WorkspaceClientReportLinkEntity> findAllByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);

    Optional<WorkspaceClientReportLinkEntity> findByIdAndWorkspaceId(String id, String workspaceId);

    List<WorkspaceClientReportLinkEntity> findAllByScheduleIdOrderByCreatedAtDesc(Long scheduleId);

    List<WorkspaceClientReportLinkEntity> findAllByExpiresAtBefore(OffsetDateTime cutoff);
}
