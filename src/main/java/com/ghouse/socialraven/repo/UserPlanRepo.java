package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.UserPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPlanRepo extends JpaRepository<UserPlanEntity, Long> {
    Optional<UserPlanEntity> findByUserId(String userId);
}
