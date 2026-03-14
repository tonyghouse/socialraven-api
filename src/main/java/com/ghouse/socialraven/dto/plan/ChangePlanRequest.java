package com.ghouse.socialraven.dto.plan;

import com.ghouse.socialraven.constant.PlanType;
import lombok.Data;

@Data
public class ChangePlanRequest {
    private PlanType planType;
}
