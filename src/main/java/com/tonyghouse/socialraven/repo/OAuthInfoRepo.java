package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OAuthInfoRepo extends JpaRepository<OAuthInfoEntity, Long> {

    // ── Workspace-scoped queries (primary — used after V8 migration) ───────────
    List<OAuthInfoEntity> findAllByWorkspaceId(String workspaceId);
    List<OAuthInfoEntity> findAllByWorkspaceIdAndProvider(String workspaceId, Provider provider);
    OAuthInfoEntity findByWorkspaceIdAndProviderAndProviderUserId(String workspaceId, Provider provider, String providerUserId);
    OAuthInfoEntity findByWorkspaceIdAndProviderUserId(String workspaceId, String providerUserId);

    // ── Legacy user-scoped queries (kept for token-refresh scheduler and backfill) ──
    List<OAuthInfoEntity> findAllByUserIdAndProvider(String userId, Provider provider);
    List<OAuthInfoEntity> findAllByUserId(String userId);
    OAuthInfoEntity findByUserIdAndProviderAndProviderUserId(String userId, Provider provider, String providerUserId);
    OAuthInfoEntity findByUserIdAndProviderUserId(String userId, String providerUserId);

    void deleteAllByWorkspaceId(String workspaceId);
}
