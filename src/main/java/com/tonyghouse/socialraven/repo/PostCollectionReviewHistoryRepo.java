package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.PostCollectionReviewHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostCollectionReviewHistoryRepo extends JpaRepository<PostCollectionReviewHistoryEntity, Long> {
    List<PostCollectionReviewHistoryEntity> findAllByPostCollectionIdOrderByCreatedAtAsc(Long postCollectionId);
}
