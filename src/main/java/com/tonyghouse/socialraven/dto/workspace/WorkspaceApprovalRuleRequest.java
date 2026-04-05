package com.tonyghouse.socialraven.dto.workspace;

import lombok.Data;

@Data
public class WorkspaceApprovalRuleRequest {
    private String scopeType;
    private String scopeValue;
    private String approvalMode;
}
