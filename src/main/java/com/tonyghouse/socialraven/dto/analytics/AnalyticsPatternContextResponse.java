package com.tonyghouse.socialraven.dto.analytics;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsPatternContextResponse {
    private String contextKey;
    private String contextLabel;
    private double baselineValue;
    private long eligiblePostCount;
    private long excludedPostCount;
    private List<AnalyticsPatternResponse> postingWindowPatterns = new ArrayList<>();
    private List<AnalyticsPatternResponse> formatPatterns = new ArrayList<>();
    private List<AnalyticsPatternResponse> accountPatterns = new ArrayList<>();
}
