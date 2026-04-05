package com.tonyghouse.socialraven.dto.clientconnect;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicClientConnectionSessionResponse {
    private String clientLabel;
    private String agencyLabel;
    private String workspaceName;
    private String companyName;
    private String logoUrl;
    private String recipientName;
    private String recipientEmail;
    private String message;
    private List<String> allowedPlatforms;
    private OffsetDateTime linkExpiresAt;
    private boolean linkRevoked;
    private boolean linkExpired;
    private boolean canConnect;
    private List<ClientConnectionActivityResponse> recentActivity;
}
