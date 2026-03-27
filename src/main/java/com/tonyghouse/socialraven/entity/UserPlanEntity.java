package com.tonyghouse.socialraven.entity;

import com.tonyghouse.socialraven.constant.PlanStatus;
import com.tonyghouse.socialraven.constant.PlanType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "user_plan")
public class UserPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", length = 50, nullable = false)
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private PlanStatus status;

    @Column(name = "renewal_date", nullable = false)
    private OffsetDateTime renewalDate;

    @Column(name = "trial_ends_at")
    private OffsetDateTime trialEndsAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "stripe_subscription_id", length = 1000)
    private String stripeSubscriptionId;

    /**
     * Custom post-collection limit per month. When set, overrides the plan_config default.
     * Useful for agency customers or promotional allowances.
     */
    @Column(name = "custom_posts_limit")
    private Integer customPostsLimit;

    /**
     * Custom connected-accounts limit. When set, overrides the plan_config default.
     */
    @Column(name = "custom_accounts_limit")
    private Integer customAccountsLimit;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
