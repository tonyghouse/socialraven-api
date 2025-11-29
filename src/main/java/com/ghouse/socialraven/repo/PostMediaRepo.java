package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostMediaRepo extends JpaRepository<PostEntity, Long> {
}
