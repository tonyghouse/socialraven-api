package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.dto.profile.AddProfileEmailRequest;
import com.tonyghouse.socialraven.dto.profile.ProfileResponse;
import com.tonyghouse.socialraven.dto.profile.UpdateProfileNameRequest;
import com.tonyghouse.socialraven.service.profile.ProfileService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private ProfileService profileService;

    @GetMapping("/me")
    public ProfileResponse getMyProfile() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return profileService.getProfile(userId);
    }

    @PatchMapping("/me")
    public ProfileResponse updateMyName(@RequestBody UpdateProfileNameRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return profileService.updateName(userId, request.getFirstName(), request.getLastName());
    }

    @PostMapping("/me/emails")
    public ProfileResponse addMyEmail(@RequestBody AddProfileEmailRequest request) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return profileService.addEmail(userId, request.getEmailAddress());
    }

    @PatchMapping("/me/emails/{emailAddressId}/primary")
    public ProfileResponse setMyPrimaryEmail(@PathVariable String emailAddressId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return profileService.setPrimaryEmail(userId, emailAddressId);
    }

    @DeleteMapping("/me/emails/{emailAddressId}")
    public ProfileResponse deleteMyEmail(@PathVariable String emailAddressId) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return profileService.deleteEmail(userId, emailAddressId);
    }

    @PostMapping(value = "/me/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse uploadMyProfileImage(@RequestPart("file") MultipartFile file) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return profileService.uploadProfileImage(userId, file);
    }

    @DeleteMapping("/me/image")
    public ProfileResponse deleteMyProfileImage() {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        return profileService.deleteProfileImage(userId);
    }
}
