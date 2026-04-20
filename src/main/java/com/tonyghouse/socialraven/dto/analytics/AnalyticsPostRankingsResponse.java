package com.tonyghouse.socialraven.dto.analytics;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsPostRankingsResponse {
    private String metric;
    private List<AnalyticsPostRowResponse> topPosts = new ArrayList<>();
    private List<AnalyticsPostRowResponse> worstPosts = new ArrayList<>();
}
