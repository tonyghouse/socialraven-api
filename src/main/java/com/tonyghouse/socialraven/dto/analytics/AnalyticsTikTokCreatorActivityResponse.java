package com.tonyghouse.socialraven.dto.analytics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsTikTokCreatorActivityResponse {
    private String currentRangeLabel;
    private LocalDate currentStartDate;
    private LocalDate currentEndDate;
    private long trackedAccounts;
    private long totalFollowers;
    private long totalFollowing;
    private long totalLikesTotal;
    private long totalVideoCount;
    private long totalFollowerDelta;
    private long totalLikesDelta;
    private long totalVideoDelta;
    private List<AnalyticsTikTokCreatorActivityTrendPointResponse> trend = new ArrayList<>();
    private List<AnalyticsTikTokCreatorActivityRowResponse> rows = new ArrayList<>();
}
