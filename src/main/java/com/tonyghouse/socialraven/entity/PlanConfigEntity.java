package com.tonyghouse.socialraven.entity;

import com.tonyghouse.socialraven.constant.PlanType;
import com.tonyghouse.socialraven.constant.UserType;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "plan_config")
public class PlanConfigEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", length = 50, nullable = false)
    private PlanType planType;

    /** Monthly post-collection limit; null = unlimited; -1 = unlimited (agency custom) */
    @Column(name = "posts_per_month")
    private Integer postsPerMonth;

    /** Connected accounts limit; null = unlimited; -1 = unlimited (agency custom) */
    @Column(name = "accounts_limit")
    private Integer accountsLimit;

    @Column(name = "price_usd", nullable = false)
    private BigDecimal priceUsd;

    /** Trial duration in days; null for paid plans */
    @Column(name = "trial_days")
    private Integer trialDays;

    /** Max workspaces allowed; 1 for influencer plans, -1 = unlimited */
    @Column(name = "max_workspaces", nullable = false)
    private Integer maxWorkspaces;

    /** Which user type this plan is intended for */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 20, nullable = false)
    private UserType userType;
}
