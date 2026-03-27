package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepo extends JpaRepository<UserProfileEntity, String> {
}
