package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.WorkspaceClientReportScheduleEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceClientReportScheduleRepo extends JpaRepository<WorkspaceClientReportScheduleEntity, Long> {
    List<WorkspaceClientReportScheduleEntity> findAllByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);

    Optional<WorkspaceClientReportScheduleEntity> findByIdAndWorkspaceId(Long id, String workspaceId);

    List<WorkspaceClientReportScheduleEntity> findAllByActiveTrueAndNextSendAtLessThanEqualOrderByNextSendAtAsc(OffsetDateTime cutoff);
}
