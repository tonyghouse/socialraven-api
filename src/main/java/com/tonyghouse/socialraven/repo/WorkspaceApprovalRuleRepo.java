package com.tonyghouse.socialraven.repo;

import com.tonyghouse.socialraven.constant.WorkspaceApprovalRuleScope;
import com.tonyghouse.socialraven.entity.WorkspaceApprovalRuleEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceApprovalRuleRepo extends JpaRepository<WorkspaceApprovalRuleEntity, Long> {
    List<WorkspaceApprovalRuleEntity> findAllByWorkspaceIdOrderByScopeTypeAscScopeValueAsc(String workspaceId);

    Optional<WorkspaceApprovalRuleEntity> findByWorkspaceIdAndScopeTypeAndScopeValue(String workspaceId,
                                                                                    WorkspaceApprovalRuleScope scopeType,
                                                                                    String scopeValue);

    void deleteAllByWorkspaceId(String workspaceId);
}
