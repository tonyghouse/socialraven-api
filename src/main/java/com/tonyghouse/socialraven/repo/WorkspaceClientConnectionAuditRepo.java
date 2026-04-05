package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.WorkspaceClientConnectionAuditEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceClientConnectionAuditRepo extends JpaRepository<WorkspaceClientConnectionAuditEntity, Long> {
    List<WorkspaceClientConnectionAuditEntity> findAllBySessionIdOrderByCreatedAtDesc(String sessionId);

    List<WorkspaceClientConnectionAuditEntity> findAllBySessionIdInOrderByCreatedAtDesc(Collection<String> sessionIds);
}
