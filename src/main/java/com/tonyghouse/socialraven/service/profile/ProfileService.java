package com.tonyghouse.socialraven.service.profile;

import com.tonyghouse.socialraven.dto.profile.ProfileEmailResponse;
import com.tonyghouse.socialraven.dto.profile.ProfileResponse;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.service.ClerkUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class ProfileService {

    @Autowired
    private ClerkUserService clerkUserService;

    public ProfileResponse getProfile(String userId) {
        return toResponse(requireProfile(userId));
    }

    public ProfileResponse updateName(String userId, String firstName, String lastName) {
        String normalizedFirstName = normalizeName(firstName, "First name");
        String normalizedLastName = normalizeName(lastName, "Last name");
        return toResponse(clerkUserService.updateUserName(userId, normalizedFirstName, normalizedLastName));
    }

    public ProfileResponse addEmail(String userId, String emailAddress) {
        String normalizedEmail = normalizeEmail(emailAddress);
        return toResponse(clerkUserService.addEmailAddress(userId, normalizedEmail));
    }

    public ProfileResponse setPrimaryEmail(String userId, String emailAddressId) {
        if (emailAddressId == null || emailAddressId.isBlank()) {
            throw new SocialRavenException("Email address ID is required.", HttpStatus.BAD_REQUEST);
        }
        return toResponse(clerkUserService.setPrimaryEmailAddress(userId, emailAddressId));
    }

    public ProfileResponse deleteEmail(String userId, String emailAddressId) {
        if (emailAddressId == null || emailAddressId.isBlank()) {
            throw new SocialRavenException("Email address ID is required.", HttpStatus.BAD_REQUEST);
        }
        return toResponse(clerkUserService.deleteEmailAddress(userId, emailAddressId));
    }

    public ProfileResponse uploadProfileImage(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new SocialRavenException("Profile image is required.", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        if (!contentType.startsWith("image/")) {
            throw new SocialRavenException("Only image uploads are supported.", HttpStatus.BAD_REQUEST);
        }
        return toResponse(clerkUserService.uploadUserProfileImage(userId, file));
    }

    public ProfileResponse deleteProfileImage(String userId) {
        return toResponse(clerkUserService.deleteUserProfileImage(userId));
    }

    private ClerkUserService.UserProfileDetails requireProfile(String userId) {
        ClerkUserService.UserProfileDetails details = clerkUserService.getUserProfileDetails(userId);
        if (details == null) {
            throw new SocialRavenException("Profile not found.", HttpStatus.NOT_FOUND);
        }
        return details;
    }

    private ProfileResponse toResponse(ClerkUserService.UserProfileDetails details) {
        List<ProfileEmailResponse> emails = details.emailAddresses().stream()
                .map(email -> new ProfileEmailResponse(
                        email.id(),
                        email.emailAddress(),
                        email.primary(),
                        email.verified()
                ))
                .toList();

        return new ProfileResponse(
                details.userId(),
                details.firstName(),
                details.lastName(),
                details.imageUrl(),
                emails
        );
    }

    private String normalizeName(String value, String label) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isBlank()) {
            throw new SocialRavenException(label + " is required.", HttpStatus.BAD_REQUEST);
        }
        if (normalized.length() > 100) {
            throw new SocialRavenException(label + " is too long.", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        String normalized = value != null ? value.trim().toLowerCase() : "";
        if (normalized.isBlank() || !normalized.contains("@")) {
            throw new SocialRavenException("A valid email address is required.", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }
}
