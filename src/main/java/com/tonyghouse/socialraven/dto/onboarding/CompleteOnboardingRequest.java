package com.tonyghouse.socialraven.dto.onboarding;

import com.tonyghouse.socialraven.constant.UserType;
import lombok.Data;

import java.util.List;

@Data
public class CompleteOnboardingRequest {
    /** INFLUENCER or AGENCY — chosen at step 1 of onboarding */
    private UserType userType;

    /**
     * For AGENCY: list of workspace names to create upfront (1–10).
     * The first entry becomes the active workspace returned in the response.
     */
    private List<String> workspaceNames;

    /** Optional company name shown as subtitle in the workspace switcher */
    private String companyName;
}
