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
    private String stripeSubscriptionId;
    /** Effective posts/month limit; null = unlimited */
    private Integer postsLimit;
    /** Effective connected-accounts limit; null = unlimited */
    private Integer accountsLimit;
}
