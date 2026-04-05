package com.tonyghouse.socialraven.dto.workspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceApprovalRuleResponse {
    private Long id;
    private String scopeType;
    private String scopeValue;
    private String approvalMode;
}
