package com.tonyghouse.socialraven.dto.analytics;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsPatternLabResponse {
    private String currentRangeLabel;
    private OffsetDateTime currentStartAt;
    private OffsetDateTime currentEndAt;
    private String scope;
    private String scopeLabel;
    private String metric;
    private String metricLabel;
    private String metricFormat;
    private int minimumSampleSize;
    private long eligiblePostCount;
    private long excludedPostCount;
    private String eligibilityNote;
    private List<AnalyticsPatternContextResponse> contexts = new ArrayList<>();
}
