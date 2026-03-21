package com.ghouse.socialraven.dto.onboarding;

import com.ghouse.socialraven.constant.UserType;
import lombok.Data;

@Data
public class CompleteOnboardingRequest {
    /** INFLUENCER or AGENCY — chosen at step 1 of onboarding */
    private UserType userType;

    /**
     * Required for AGENCY; ignored for INFLUENCER (defaults to "main").
     * The display name of the first workspace.
     */
    private String workspaceName;

    /** Optional company name shown as subtitle in the workspace switcher */
    private String companyName;
}
