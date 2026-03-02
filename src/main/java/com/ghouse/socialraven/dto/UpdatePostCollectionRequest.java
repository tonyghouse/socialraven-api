package com.ghouse.socialraven.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class UpdatePostCollectionRequest {

    private String title;

    private String description;

    private OffsetDateTime scheduledTime;
}
