package com.tonyghouse.socialraven.dto;

import com.tonyghouse.socialraven.constant.WorkspaceApprovalMode;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ScheduleDraftRequest {
    private OffsetDateTime scheduledTime;
    private WorkspaceApprovalMode approvalModeOverride;
    private Boolean clearApprovalModeOverride;
}
