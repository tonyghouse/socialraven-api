package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.AccountAnalyticsSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AccountAnalyticsSnapshotRepo extends JpaRepository<AccountAnalyticsSnapshotEntity, Long> {

    List<AccountAnalyticsSnapshotEntity> findByProviderUserIdAndProviderAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            String providerUserId, String provider, LocalDate from, LocalDate to);

    List<AccountAnalyticsSnapshotEntity> findByProviderUserIdInAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            List<String> providerUserIds, LocalDate from, LocalDate to);

    @Modifying
    @Query(value = "DELETE FROM socialraven.account_analytics_snapshots WHERE workspace_id = :workspaceId", nativeQuery = true)
    void deleteAllByWorkspaceId(@Param("workspaceId") String workspaceId);
}
