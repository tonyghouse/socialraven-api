package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.WorkspaceMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceMemberRepo extends JpaRepository<WorkspaceMemberEntity, Long> {
    List<WorkspaceMemberEntity> findAllByUserId(String userId);
    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserId(String workspaceId, String userId);
    List<WorkspaceMemberEntity> findAllByWorkspaceId(String workspaceId);
    void deleteAllByWorkspaceId(String workspaceId);
}
