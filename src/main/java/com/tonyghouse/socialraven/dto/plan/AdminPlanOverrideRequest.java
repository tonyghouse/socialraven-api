package com.tonyghouse.socialraven.dto.plan;

import com.tonyghouse.socialraven.constant.PlanStatus;
import com.tonyghouse.socialraven.constant.PlanType;
import lombok.Data;

@Data
public class AdminPlanOverrideRequest {
    /** New plan type to assign */
    private PlanType planType;
    /** Override plan status (e.g. ACTIVE, CANCELLED) */
    private PlanStatus status;
    /** Custom posts/month limit; null = use plan default */
    private Integer customPostsLimit;
    /** Custom connected-accounts limit; null = use plan default */
    private Integer customAccountsLimit;
    /**
     * Custom x.com posts/month limit.
     * On company-scoped admin endpoints this applies to the whole company plan.
     * On workspace-scoped admin endpoints this applies only to that workspace.
     */
    private Integer customXPostsLimit;
    /** Optional ISO-8601 renewal date override */
    private String renewalDate;
}
