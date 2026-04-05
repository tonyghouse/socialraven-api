package com.tonyghouse.socialraven.dto.workspace;

import com.tonyghouse.socialraven.constant.WorkspaceApprovalMode;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceResponse {
    private String id;
    private String companyId;
    private String name;
    private String companyName;
    private String companyLogoS3Key;
    private String logoS3Key;
    private WorkspaceRole role;
    private WorkspaceApprovalMode approvalMode;
    private boolean autoScheduleAfterApproval;
    private boolean ownerFinalApprovalRequired;
    private List<String> approverUserIds;
    private List<String> publisherUserIds;
    private List<WorkspaceApprovalRuleResponse> approvalRules;
    private List<WorkspaceCapability> capabilities;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
