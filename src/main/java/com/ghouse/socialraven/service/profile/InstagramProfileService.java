package com.ghouse.socialraven.service.profile;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class InstagramProfileService {

    private final RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {
        try {
            String userId = info.getProviderUserId();
            String accessToken = info.getAccessToken();

            log.info("Fetching Instagram profile for user ID: {}", userId);

            // Fetch Instagram Business Account profile
            String url = String.format(
                "https://graph.instagram.com/%s?fields=id,username,name,profile_picture_url,followers_count,follows_count,media_count,account_type&access_token=%s",
                userId, accessToken
            );

            log.debug("Instagram profile API URL: {}", url.replace(accessToken, "***"));

            ResponseEntity<Map> response = rest.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                Map.class
            );

            Map body = response.getBody();
            log.info("Instagram profile fetched successfully for user: {}", body.get("username"));
            log.debug("Instagram profile response: {}", body);

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(info.getProviderUserId());
            dto.setPlatform(Platform.instagram);
            dto.setUsername((String) body.get("username"));
            
            // Instagram provides profile_picture_url directly
            dto.setProfilePicLink((String) body.get("profile_picture_url"));
            
            // Optional: Add additional metadata if your DTO supports it
            // dto.setDisplayName((String) body.get("name"));
            // dto.setFollowersCount((Integer) body.get("followers_count"));
            // dto.setAccountType((String) body.get("account_type")); // BUSINESS, CREATOR, etc.

            return dto;

        } catch (Exception exp) {
            log.error("Instagram Profile fetching Failed: {}", exp.getMessage(), exp);
            return null;
        }
    }
}