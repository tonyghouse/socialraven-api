package com.tonyghouse.socialraven.dto.analytics;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsTrendExplorerPointResponse {
    private String bucketKey;
    private LocalDate bucketStartDate;
    private LocalDate bucketEndDate;
    private double performanceValue;
    private long postsPublished;
    private Double averagePerformancePerPost;
}
