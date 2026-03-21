package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.WorkspaceSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceSettingsRepo extends JpaRepository<WorkspaceSettingsEntity, String> {
}
