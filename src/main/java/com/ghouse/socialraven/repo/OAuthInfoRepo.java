package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OAuthInfoRepo extends JpaRepository<OAuthInfoEntity, Long> {
    List<OAuthInfoEntity> findAllByUserIdAndProvider(String userId, Provider provider);

    List<OAuthInfoEntity> findAllByUserId(String userId);

    OAuthInfoEntity findByUserIdAndProviderAndProviderUserId(String userId, Provider provider, String providerUserId);
    OAuthInfoEntity findByUserIdAndProviderUserId(String userId, String providerUserId);
    List<OAuthInfoEntity> findByUserIdAndProviderUserIdIn(String userId, List<String> providerUserIds);
}
