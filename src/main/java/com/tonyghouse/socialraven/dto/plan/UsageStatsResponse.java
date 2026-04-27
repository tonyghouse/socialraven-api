package com.tonyghouse.socialraven.dto.plan;

import lombok.Data;

@Data
public class UsageStatsResponse {
    private long postsUsedThisMonth;
    /** null = unlimited */
    private Integer postsLimit;
    private long connectedAccountsCount;
    /** null = unlimited */
    private Integer accountsLimit;
    private long xPostsUsedThisMonth;
    /** null = unlimited */
    private Integer xPostsLimit;
    /** Number of workspaces owned by the workspace owner */
    private int workspacesOwned;
    /** Max workspaces allowed by the owner's plan; -1 = unlimited */
    private Integer maxWorkspaces;
}
