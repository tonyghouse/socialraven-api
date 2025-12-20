package com.ghouse.socialraven.dto;

import lombok.Data;

import java.util.List;

/**
 * Response from LinkedIn video status check
 */
@Data
public class LinkedInVideoStatusResponse {
    private String created;
    private String id;
    private String lastModified;
    private String mediaTypeFamily;
    private List<RecipeInfo> recipes;
    private List<ServiceRelationship> serviceRelationships;
    private String status;  // PROCESSING, AVAILABLE, FAILED

    @Data
    public static class RecipeInfo {
        private String recipe;
        private String status;  // PROCESSING, AVAILABLE, FAILED
    }

    @Data
    public static class ServiceRelationship {
        private String identifier;
        private String relationshipType;
    }
}