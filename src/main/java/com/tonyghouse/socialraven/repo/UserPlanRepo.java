package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.UserPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPlanRepo extends JpaRepository<UserPlanEntity, Long> {
    Optional<UserPlanEntity> findByCompanyId(String companyId);
    void deleteAllByCompanyId(String companyId);
}
