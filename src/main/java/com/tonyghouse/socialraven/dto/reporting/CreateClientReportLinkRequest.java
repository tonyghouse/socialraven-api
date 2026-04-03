package com.tonyghouse.socialraven.dto.reporting;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class CreateClientReportLinkRequest {
    private String reportTitle;
    private String clientLabel;
    private String agencyLabel;
    private String templateType;
    private Integer reportDays;
    private String commentary;
    private OffsetDateTime expiresAt;
    private String recipientName;
    private String recipientEmail;
}
