package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.PostCollaborationThreadEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostCollaborationThreadRepo extends JpaRepository<PostCollaborationThreadEntity, Long> {
    List<PostCollaborationThreadEntity> findAllByPostCollectionId(Long postCollectionId);

    Optional<PostCollaborationThreadEntity> findByIdAndPostCollectionId(Long id, Long postCollectionId);
}
