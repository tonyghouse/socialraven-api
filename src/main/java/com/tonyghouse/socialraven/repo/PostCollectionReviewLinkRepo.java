package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.PostCollectionReviewLinkEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCollectionReviewLinkRepo extends JpaRepository<PostCollectionReviewLinkEntity, String> {

    List<PostCollectionReviewLinkEntity> findAllByPostCollectionIdOrderByCreatedAtDesc(Long postCollectionId);

    Optional<PostCollectionReviewLinkEntity> findByIdAndPostCollectionId(String id, Long postCollectionId);
}
