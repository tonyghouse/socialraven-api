package com.tonyghouse.socialraven.dto.analytics;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsFilterOptionsResponse {
    private List<AnalyticsSelectOptionResponse> platforms = new ArrayList<>();
    private List<AnalyticsSelectOptionResponse> accounts = new ArrayList<>();
    private List<AnalyticsSelectOptionResponse> campaigns = new ArrayList<>();
    private List<AnalyticsSelectOptionResponse> contentTypes = new ArrayList<>();
}
