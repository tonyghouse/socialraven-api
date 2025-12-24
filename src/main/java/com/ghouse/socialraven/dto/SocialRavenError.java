package com.ghouse.socialraven.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class SocialRavenError {
    private String message;
    private String code;
    private Instant timestamp;
}
