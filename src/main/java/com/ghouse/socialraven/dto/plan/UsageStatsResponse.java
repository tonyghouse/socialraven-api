package com.ghouse.socialraven.dto.plan;

import lombok.Data;

@Data
public class UsageStatsResponse {
    private long postsUsedThisMonth;
    /** null = unlimited */
    private Integer postsLimit;
    private long connectedAccountsCount;
    /** null = unlimited */
    private Integer accountsLimit;
}
