package com.ghouse.socialraven.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LinkedInUploadResponse {
    private Value value;

    @Data
    public static class Value {
        private String asset;

        // ✅ THIS IS THE REAL FIELD NOW
        private List<UploadInstruction> uploadInstructions;
    }

    @Data
    public static class UploadInstruction {
        private String uploadUrl;
        private Map<String, String> headers;
    }
}
