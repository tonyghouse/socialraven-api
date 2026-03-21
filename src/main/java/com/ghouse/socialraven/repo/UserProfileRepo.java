package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepo extends JpaRepository<UserProfileEntity, String> {
}
