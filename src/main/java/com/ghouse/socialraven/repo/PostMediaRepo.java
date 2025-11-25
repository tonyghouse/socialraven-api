package com.ghouse.socialraven.repo;

import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.entity.SinglePostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostMediaRepo extends JpaRepository<SinglePostEntity, Long> {
}
