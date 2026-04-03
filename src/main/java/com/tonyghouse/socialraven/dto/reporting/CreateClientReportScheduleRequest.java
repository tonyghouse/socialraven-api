package com.tonyghouse.socialraven.dto.reporting;

import lombok.Data;

@Data
public class CreateClientReportScheduleRequest {
    private String reportTitle;
    private String recipientName;
    private String recipientEmail;
    private String clientLabel;
    private String agencyLabel;
    private String templateType;
    private Integer reportDays;
    private String commentary;
    private String cadence;
    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private Integer hourOfDayUtc;
    private Integer shareExpiryHours;
}
