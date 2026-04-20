package com.tonyghouse.socialraven.dto.reporting;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportScheduleResponse {
    private Long id;
    private String reportTitle;
    private String recipientName;
    private String recipientEmail;
    private String clientLabel;
    private String agencyLabel;
    private String reportScope;
    private Long campaignId;
    private String campaignLabel;
    private String templateType;
    private Integer reportDays;
    private String cadence;
    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private Integer hourOfDayUtc;
    private Integer shareExpiryHours;
    private boolean active;
    private OffsetDateTime lastSentAt;
    private OffsetDateTime nextSendAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
