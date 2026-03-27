package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.constant.PlanType;
import com.tonyghouse.socialraven.entity.PlanConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanConfigRepo extends JpaRepository<PlanConfigEntity, PlanType> {
}
