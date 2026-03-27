package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.UserPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPlanRepo extends JpaRepository<UserPlanEntity, Long> {
    Optional<UserPlanEntity> findByUserId(String userId);
    Optional<UserPlanEntity> findByWorkspaceId(String workspaceId);
    void deleteAllByWorkspaceId(String workspaceId);
}
