package com.ghouse.socialraven.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ScheduleDraftRequest {
    private OffsetDateTime scheduledTime;
}
