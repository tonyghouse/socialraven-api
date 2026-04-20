package com.tonyghouse.socialraven.dto.analytics;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsPostTableResponse {
    private String sortBy;
    private String sortDirection;
    private int page;
    private int size;
    private long totalCount;
    private boolean hasNext;
    private List<AnalyticsPostRowResponse> rows = new ArrayList<>();
}
