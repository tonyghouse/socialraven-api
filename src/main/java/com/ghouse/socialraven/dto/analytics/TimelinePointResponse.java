package com.ghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimelinePointResponse {
    private String date;
    private String provider;
    private long totalEngagements;
}
