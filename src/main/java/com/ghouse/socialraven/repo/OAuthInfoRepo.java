package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OAuthInfoRepo extends JpaRepository<OAuthInfo, Long> {
    OAuthInfo findByUserIdAndProvider(String userId, Provider provider);

}
