package com.ghouse.socialraven.dto.workspace;

import lombok.Data;

import java.util.UUID;

@Data
public class AcceptInviteRequest {
    private UUID token;
}
