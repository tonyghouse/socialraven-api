package com.tonyghouse.socialraven.dto.profile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileEmailResponse {
    private String id;
    private String emailAddress;
    private boolean primary;
    private boolean verified;
}
