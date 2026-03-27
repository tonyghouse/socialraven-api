package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.WorkspaceSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceSettingsRepo extends JpaRepository<WorkspaceSettingsEntity, String> {
    void deleteById(String workspaceId);
}
