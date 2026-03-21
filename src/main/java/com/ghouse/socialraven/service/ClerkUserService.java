package com.ghouse.socialraven.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Calls the Clerk REST API to look up user details (e.g. primary email address).
 */
@Service
@Slf4j
public class ClerkUserService {

    private static final String CLERK_API = "https://api.clerk.com/v1/users/";

    @Value("${socialraven.clerk.secret}")
    private String clerkSecretKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns the primary email address of the Clerk user with the given ID.
     * Returns null if the user cannot be found or has no email.
     */
    public String getUserEmail(String clerkUserId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CLERK_API + clerkUserId))
                    .header("Authorization", "Bearer " + clerkSecretKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Clerk user lookup failed: status={}, userId={}", response.statusCode(), clerkUserId);
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            String primaryEmailId = root.path("primary_email_address_id").asText(null);

            JsonNode emailAddresses = root.path("email_addresses");
            if (emailAddresses.isArray()) {
                for (JsonNode emailNode : emailAddresses) {
                    if (primaryEmailId != null && primaryEmailId.equals(emailNode.path("id").asText(null))) {
                        return emailNode.path("email_address").asText(null);
                    }
                }
                // Fallback: return first email if no primary match
                if (emailAddresses.size() > 0) {
                    return emailAddresses.get(0).path("email_address").asText(null);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching Clerk user email for userId={}: {}", clerkUserId, e.getMessage(), e);
        }
        return null;
    }
}
