package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.CompanyUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyUserRepo extends JpaRepository<CompanyUserEntity, Long> {
    List<CompanyUserEntity> findAllByUserId(String userId);
    List<CompanyUserEntity> findAllByCompanyId(String companyId);
    Optional<CompanyUserEntity> findByCompanyIdAndUserId(String companyId, String userId);
    void deleteAllByCompanyId(String companyId);
}
