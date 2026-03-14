package com.ghouse.socialraven.entity;

import com.ghouse.socialraven.constant.PlanType;
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

    /** Monthly post-collection limit; null = unlimited */
    @Column(name = "posts_per_month")
    private Integer postsPerMonth;

    /** Connected accounts limit; null = unlimited */
    @Column(name = "accounts_limit")
    private Integer accountsLimit;

    @Column(name = "price_usd", nullable = false)
    private BigDecimal priceUsd;

    /** Trial duration in days; null for paid plans */
    @Column(name = "trial_days")
    private Integer trialDays;
}
