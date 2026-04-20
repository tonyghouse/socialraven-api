package com.tonyghouse.socialraven.dto.reporting;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class ClientReportSnapshotRequest {
    private String reportTitle;
    private String clientLabel;
    private String agencyLabel;
    private String reportScope;
    private Long campaignId;
    private String templateType;
    private Integer reportDays;
    private String commentary;
    private OffsetDateTime expiresAt;
}
