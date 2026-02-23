package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.AccountGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountGroupRepo extends JpaRepository<AccountGroupEntity, Long> {

    List<AccountGroupEntity> findAllByUserId(String userId);

    Optional<AccountGroupEntity> findByIdAndUserId(Long id, String userId);

    @Query("SELECT g FROM AccountGroupEntity g WHERE g.userId = :userId AND :providerUserId MEMBER OF g.accountIds")
    List<AccountGroupEntity> findByUserIdAndAccountIdMember(
            @Param("userId") String userId,
            @Param("providerUserId") String providerUserId
    );
}
