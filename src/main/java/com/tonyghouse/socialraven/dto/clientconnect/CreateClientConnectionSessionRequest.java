package com.tonyghouse.socialraven.dto.clientconnect;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

@Data
public class CreateClientConnectionSessionRequest {
    private String recipientName;
    private String recipientEmail;
    private String clientLabel;
    private String agencyLabel;
    private String message;
    private List<String> allowedPlatforms;
    private OffsetDateTime expiresAt;
}
