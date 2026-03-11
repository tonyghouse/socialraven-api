package com.ghouse.socialraven.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HeatmapCellResponse {
    private int dayOfWeek;   // 1=Monday ... 7=Sunday
    private int hourOfDay;   // 0-23
    private double avgEngagement;
    private int postCount;
}
