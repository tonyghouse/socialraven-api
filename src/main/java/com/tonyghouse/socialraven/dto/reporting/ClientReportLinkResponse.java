package com.tonyghouse.socialraven.dto.reporting;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportLinkResponse {
    private String id;
    private String token;
    private String reportTitle;
    private String clientLabel;
    private String agencyLabel;
    private String reportScope;
    private Long campaignId;
    private String campaignLabel;
    private String templateType;
    private Integer reportDays;
    private String recipientName;
    private String recipientEmail;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime lastAccessedAt;
    private OffsetDateTime createdAt;
    private boolean active;
}
