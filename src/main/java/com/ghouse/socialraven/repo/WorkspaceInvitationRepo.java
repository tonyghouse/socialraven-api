package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.WorkspaceInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceInvitationRepo extends JpaRepository<WorkspaceInvitationEntity, UUID> {

    /** All pending (not yet accepted) invitations for a workspace. */
    List<WorkspaceInvitationEntity> findAllByWorkspaceIdAndAcceptedAtIsNull(String workspaceId);

    /** Find a specific invitation by its token. */
    Optional<WorkspaceInvitationEntity> findByToken(UUID token);
}
