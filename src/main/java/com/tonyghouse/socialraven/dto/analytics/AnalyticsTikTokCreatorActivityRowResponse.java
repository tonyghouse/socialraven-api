package com.tonyghouse.socialraven.dto.analytics;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsTikTokCreatorActivityRowResponse {
    private String providerUserId;
    private String accountName;
    private Long followers;
    private Long following;
    private Long likesTotal;
    private Long videoCount;
    private Long followerDelta;
    private Long likesDelta;
    private Long videoDelta;
    private Double followerSharePercent;
    private Double likesSharePercent;
    private LocalDate lastSnapshotDate;
}
