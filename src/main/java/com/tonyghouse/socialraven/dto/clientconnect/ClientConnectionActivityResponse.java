package com.tonyghouse.socialraven.dto.clientconnect;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientConnectionActivityResponse {
    private String platform;
    private String providerUserId;
    private String eventType;
    private String actorDisplayName;
    private String actorEmail;
    private OffsetDateTime createdAt;
}
