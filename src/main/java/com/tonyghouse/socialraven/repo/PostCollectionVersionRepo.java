package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.PostCollectionVersionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostCollectionVersionRepo extends JpaRepository<PostCollectionVersionEntity, Long> {
    List<PostCollectionVersionEntity> findAllByPostCollectionIdOrderByVersionNumberDesc(Long postCollectionId);

    Optional<PostCollectionVersionEntity> findFirstByPostCollectionIdOrderByVersionNumberDesc(Long postCollectionId);
}
