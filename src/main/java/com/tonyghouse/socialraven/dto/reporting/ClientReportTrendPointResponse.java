package com.tonyghouse.socialraven.dto.reporting;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportTrendPointResponse {
    private String bucketKey;
    private LocalDate bucketStartDate;
    private LocalDate bucketEndDate;
    private double engagements;
    private long postsPublished;
    private Double averageEngagementsPerPost;
}
