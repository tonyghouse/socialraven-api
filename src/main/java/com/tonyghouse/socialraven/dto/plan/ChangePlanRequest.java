package com.tonyghouse.socialraven.dto.plan;

import com.tonyghouse.socialraven.constant.PlanType;
import lombok.Data;

@Data
public class ChangePlanRequest {
    private PlanType planType;
}
