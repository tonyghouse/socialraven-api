package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.CompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyRepo extends JpaRepository<CompanyEntity, String> {
    List<CompanyEntity> findAllByOwnerUserId(String ownerUserId);
}
