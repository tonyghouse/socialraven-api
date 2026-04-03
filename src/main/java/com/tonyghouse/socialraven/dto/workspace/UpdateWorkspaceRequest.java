package com.tonyghouse.socialraven.dto.workspace;

import com.tonyghouse.socialraven.constant.WorkspaceApprovalMode;
import lombok.Data;

import java.util.List;

@Data
public class UpdateWorkspaceRequest {
    private String name;
    private String companyName;
    private String logoS3Key;
    private WorkspaceApprovalMode approvalMode;
    private List<String> approverUserIds;
}
