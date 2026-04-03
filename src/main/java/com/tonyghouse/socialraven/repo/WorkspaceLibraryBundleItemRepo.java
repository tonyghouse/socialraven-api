package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.entity.WorkspaceLibraryBundleItemEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceLibraryBundleItemRepo extends JpaRepository<WorkspaceLibraryBundleItemEntity, Long> {
    List<WorkspaceLibraryBundleItemEntity> findAllByBundleIdInOrderByPositionAscIdAsc(Collection<Long> bundleIds);

    List<WorkspaceLibraryBundleItemEntity> findAllByBundleIdOrderByPositionAscIdAsc(Long bundleId);

    void deleteAllByBundleId(Long bundleId);
}
