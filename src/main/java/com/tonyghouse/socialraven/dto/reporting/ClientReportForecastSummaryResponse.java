package com.tonyghouse.socialraven.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportForecastSummaryResponse {
    private String metric;
    private String metricLabel;
    private String metricFormat;
    private String planningWindowLabel;
    private String basisNote;
    private ClientReportForecastItemResponse nextPostPrediction;
    private ClientReportForecastItemResponse nextBestSlot;
    private ClientReportForecastItemResponse planningWindowProjection;
}
