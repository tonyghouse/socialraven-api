package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.constant.PlanType;
import com.ghouse.socialraven.entity.PlanConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanConfigRepo extends JpaRepository<PlanConfigEntity, PlanType> {
}
