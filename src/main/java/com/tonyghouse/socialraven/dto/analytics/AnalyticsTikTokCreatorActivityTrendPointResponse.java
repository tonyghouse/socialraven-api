package com.tonyghouse.socialraven.dto.analytics;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsTikTokCreatorActivityTrendPointResponse {
    private LocalDate snapshotDate;
    private Long followers;
    private Long likesTotal;
    private Long videoCount;
}
