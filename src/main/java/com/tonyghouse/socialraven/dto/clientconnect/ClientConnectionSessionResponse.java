package com.tonyghouse.socialraven.dto.clientconnect;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientConnectionSessionResponse {
    private String id;
    private String token;
    private String createdByUserId;
    private String createdByDisplayName;
    private String recipientName;
    private String recipientEmail;
    private String clientLabel;
    private String agencyLabel;
    private String message;
    private List<String> allowedPlatforms;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime lastAccessedAt;
    private OffsetDateTime lastConnectedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private boolean active;
    private int connectionCount;
    private List<ClientConnectionActivityResponse> recentActivity;
}
