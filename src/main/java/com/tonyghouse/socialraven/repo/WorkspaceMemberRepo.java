package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceMemberRepo extends JpaRepository<WorkspaceMemberEntity, Long> {
    List<WorkspaceMemberEntity> findAllByUserId(String userId);
    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserId(String workspaceId, String userId);
    List<WorkspaceMemberEntity> findAllByWorkspaceId(String workspaceId);
    List<WorkspaceMemberEntity> findAllByWorkspaceIdIn(List<String> workspaceIds);
    void deleteAllByWorkspaceId(String workspaceId);

    @Query(value = """
            SELECT wm.role
            FROM socialraven.workspace_user wm
            JOIN socialraven.workspace w ON w.id = wm.workspace_id
            WHERE wm.workspace_id = :workspaceId
              AND wm.user_id = :userId
              AND w.deleted_at IS NULL
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findRoleInActiveWorkspace(
            @Param("workspaceId") String workspaceId,
            @Param("userId") String userId
    );
}
