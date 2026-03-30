package com.tonyghouse.socialraven.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls the Clerk REST API to look up user details (e.g. primary email address).
 */
@Service
@Slf4j
public class ClerkUserService {

    private static final String CLERK_API = "https://api.clerk.com/v1";

    @Value("${socialraven.clerk.secret}")
    private String clerkSecretKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public record UserProfile(String firstName, String lastName, String email) {}
    public record EmailAddress(String id, String emailAddress, boolean primary, boolean verified) {}
    public record UserProfileDetails(
            String userId,
            String firstName,
            String lastName,
            String imageUrl,
            String primaryEmailAddressId,
            List<EmailAddress> emailAddresses
    ) {}

    /**
     * Returns the primary email address of the Clerk user with the given ID.
     * Returns null if the user cannot be found or has no email.
     */
    public String getUserEmail(String clerkUserId) {
        UserProfile profile = getUserProfile(clerkUserId);
        return profile != null ? profile.email() : null;
    }

    /**
     * Returns firstName, lastName, and primary email for the given Clerk user ID.
     * Returns null if the user cannot be found.
     */
    public UserProfile getUserProfile(String clerkUserId) {
        UserProfileDetails details = getUserProfileDetails(clerkUserId);
        if (details == null) {
            return null;
        }

        String primaryEmail = details.emailAddresses().stream()
                .filter(EmailAddress::primary)
                .map(EmailAddress::emailAddress)
                .findFirst()
                .orElseGet(() -> details.emailAddresses().isEmpty() ? null : details.emailAddresses().get(0).emailAddress());

        return new UserProfile(details.firstName(), details.lastName(), primaryEmail);
    }

    public UserProfileDetails getUserProfileDetails(String clerkUserId) {
        try {
            JsonNode root = sendJsonRequest("/users/" + clerkUserId, "GET", null);
            return parseUserProfile(root);
        } catch (Exception e) {
            log.error("Error fetching Clerk user profile for userId={}: {}", clerkUserId, e.getMessage(), e);
        }
        return null;
    }

    public UserProfileDetails updateUserName(String clerkUserId, String firstName, String lastName) {
        sendJsonRequest(
                "/users/" + clerkUserId,
                "PATCH",
                Map.of(
                        "first_name", firstName,
                        "last_name", lastName
                )
        );
        return getUserProfileDetails(clerkUserId);
    }

    public UserProfileDetails addEmailAddress(String clerkUserId, String emailAddress) {
        sendJsonRequest(
                "/email_addresses",
                "POST",
                Map.of(
                        "user_id", clerkUserId,
                        "email_address", emailAddress,
                        "verified", false,
                        "primary", false
                )
        );
        return getUserProfileDetails(clerkUserId);
    }

    public UserProfileDetails setPrimaryEmailAddress(String clerkUserId, String emailAddressId) {
        UserProfileDetails details = requireUserProfile(clerkUserId);
        EmailAddress email = findOwnedEmail(details, emailAddressId);
        if (!email.verified()) {
            throw new SocialRavenException("Email address must be verified before it can be made primary.", "EMAIL_NOT_VERIFIED");
        }

        sendJsonRequest(
                "/email_addresses/" + emailAddressId,
                "PATCH",
                Map.of("primary", true)
        );
        return getUserProfileDetails(clerkUserId);
    }

    public UserProfileDetails deleteEmailAddress(String clerkUserId, String emailAddressId) {
        UserProfileDetails details = requireUserProfile(clerkUserId);
        EmailAddress email = findOwnedEmail(details, emailAddressId);
        if (email.primary()) {
            throw new SocialRavenException("Primary email address cannot be deleted.", "PRIMARY_EMAIL_DELETE_NOT_ALLOWED");
        }

        sendJsonRequest("/email_addresses/" + emailAddressId, "DELETE", null);
        return getUserProfileDetails(clerkUserId);
    }

    public UserProfileDetails uploadUserProfileImage(String clerkUserId, MultipartFile file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(clerkSecretKey);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "profile-image";
                }
            };

            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(
                    file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE
            ));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new HttpEntity<>(resource, fileHeaders));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(CLERK_API + "/users/" + clerkUserId + "/profile_image"),
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new SocialRavenException("Failed to upload profile image.", "CLERK_PROFILE_IMAGE_UPLOAD_FAILED");
            }

            return getUserProfileDetails(clerkUserId);
        } catch (SocialRavenException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Error uploading Clerk profile image for userId={}: {}", clerkUserId, e.getMessage(), e);
            throw new SocialRavenException("Failed to upload profile image.", "CLERK_PROFILE_IMAGE_UPLOAD_FAILED", e);
        }
    }

    public UserProfileDetails deleteUserProfileImage(String clerkUserId) {
        sendJsonRequest("/users/" + clerkUserId + "/profile_image", "DELETE", null);
        return getUserProfileDetails(clerkUserId);
    }

    private UserProfileDetails requireUserProfile(String clerkUserId) {
        UserProfileDetails details = getUserProfileDetails(clerkUserId);
        if (details == null) {
            throw new SocialRavenException("Profile not found.", "PROFILE_NOT_FOUND");
        }
        return details;
    }

    private EmailAddress findOwnedEmail(UserProfileDetails details, String emailAddressId) {
        return details.emailAddresses().stream()
                .filter(email -> email.id().equals(emailAddressId))
                .findFirst()
                .orElseThrow(() -> new SocialRavenException("Email address not found.", "EMAIL_NOT_FOUND"));
    }

    private JsonNode sendJsonRequest(String path, String method, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(CLERK_API + path))
                    .header("Authorization", "Bearer " + clerkSecretKey)
                    .header("Accept", "application/json");

            if (body != null) {
                builder.header("Content-Type", "application/json");
            }

            HttpRequest request = switch (method) {
                case "GET" -> builder.GET().build();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
                case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
                case "DELETE" -> builder.DELETE().build();
                default -> throw new IllegalArgumentException("Unsupported Clerk request method: " + method);
            };

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Clerk API request failed: method={}, path={}, status={}, body={}",
                        method, path, response.statusCode(), response.body());
                throw new SocialRavenException("Failed to sync profile with Clerk.", "CLERK_API_REQUEST_FAILED");
            }

            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return objectMapper.nullNode();
            }
            return objectMapper.readTree(responseBody);
        } catch (SocialRavenException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Clerk API request error: method={}, path={}", method, path, e);
            throw new SocialRavenException("Failed to sync profile with Clerk.", "CLERK_API_REQUEST_FAILED", e);
        }
    }

    private UserProfileDetails parseUserProfile(JsonNode root) {
        String userId = root.path("id").asText(null);
        String firstName = root.path("first_name").asText(null);
        String lastName = root.path("last_name").asText(null);
        String imageUrl = root.path("image_url").asText(null);
        String primaryEmailId = root.path("primary_email_address_id").asText(null);

        List<EmailAddress> emailAddresses = new ArrayList<>();
        JsonNode emailNodes = root.path("email_addresses");
        if (emailNodes.isArray()) {
            for (JsonNode emailNode : emailNodes) {
                String emailId = emailNode.path("id").asText(null);
                String emailAddress = emailNode.path("email_address").asText(null);
                boolean primary = primaryEmailId != null && primaryEmailId.equals(emailId);
                boolean verified = "verified".equalsIgnoreCase(
                        emailNode.path("verification").path("status").asText("")
                );
                emailAddresses.add(new EmailAddress(emailId, emailAddress, primary, verified));
            }
        }

        return new UserProfileDetails(userId, firstName, lastName, imageUrl, primaryEmailId, emailAddresses);
    }
}
