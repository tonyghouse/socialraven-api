package com.ghouse.socialraven.dto.plan;

import com.ghouse.socialraven.constant.PlanStatus;
import com.ghouse.socialraven.constant.PlanType;
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
    /** Optional ISO-8601 renewal date override */
    private String renewalDate;
}
