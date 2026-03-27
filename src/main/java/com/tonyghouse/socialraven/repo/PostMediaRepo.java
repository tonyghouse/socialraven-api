package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.PostMediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostMediaRepo extends JpaRepository<PostMediaEntity, Long> {
}
