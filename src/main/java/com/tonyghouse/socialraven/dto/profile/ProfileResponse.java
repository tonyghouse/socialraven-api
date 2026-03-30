package com.tonyghouse.socialraven.dto.profile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    private String userId;
    private String firstName;
    private String lastName;
    private String imageUrl;
    private List<ProfileEmailResponse> emailAddresses;
}
