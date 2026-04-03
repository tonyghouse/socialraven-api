package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.PostCollaborationReplyEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostCollaborationReplyRepo extends JpaRepository<PostCollaborationReplyEntity, Long> {
    List<PostCollaborationReplyEntity> findAllByThreadIdInOrderByCreatedAtAsc(List<Long> threadIds);
}
