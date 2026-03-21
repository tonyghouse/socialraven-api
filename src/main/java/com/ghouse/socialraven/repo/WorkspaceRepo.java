package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceRepo extends JpaRepository<WorkspaceEntity, String> {
    List<WorkspaceEntity> findAllByOwnerUserId(String ownerUserId);
}
