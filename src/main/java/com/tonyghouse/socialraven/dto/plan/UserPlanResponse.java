package com.tonyghouse.socialraven.dto.plan;

import lombok.Data;

@Data
public class UserPlanResponse {
    private String currentPlan;
    private String status;
    private String renewalDate;
    private String startDate;
    private String trialEndsAt;
    private boolean cancelAtPeriodEnd;
    private String paddleSubscriptionId;
    /** Effective posts/month limit; null = unlimited */
    private Integer postsLimit;
    /** Effective connected-accounts limit; null = unlimited */
    private Integer accountsLimit;
    /**
     * Effective x.com posts/month limit.
     * When a workspace is in scope, workspace override > company override > plan default.
     */
    private Integer xPostsLimit;
}
