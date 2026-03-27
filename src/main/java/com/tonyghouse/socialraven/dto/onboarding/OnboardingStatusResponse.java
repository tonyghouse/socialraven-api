package com.tonyghouse.socialraven.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OnboardingStatusResponse {
    /** true if the user has completed onboarding (user_profile exists) */
    private boolean completed;

    /** INFLUENCER or AGENCY; null when completed=false */
    private String userType;

    /** The user's active workspace ID; null when completed=false */
    private String workspaceId;
}
