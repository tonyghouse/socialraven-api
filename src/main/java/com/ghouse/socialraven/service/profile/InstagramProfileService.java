package com.ghouse.socialraven.service.profile;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class InstagramProfileService {

    private final RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {
        try {
            String accessToken = info.getAccessToken();
            String userId = info.getProviderUserId();

            log.info("Fetching Instagram profile for user ID: {}", userId);
            log.info("Token length: {}, First 20 chars: {}",
                    accessToken.length(),
                    accessToken.substring(0, Math.min(20, accessToken.length())));

            // Use the user_id directly to fetch basic profile
            // Build URL without String.format to avoid {} issues
            String url = "https://graph.facebook.com/v21.0/" + userId +
                    "?fields=username,profile_picture_url" +
                    "&access_token=" + accessToken;

            log.info("Fetching Instagram profile directly");

            Map response = rest.getForObject(url, Map.class);

            if (response == null) {
                log.error("Empty response from Instagram API");
                return null;
            }

            log.info("Instagram profile fetched successfully: {}", response.get("username"));

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(userId);
            dto.setPlatform(Platform.instagram);
            dto.setUsername((String) response.get("username"));
            dto.setProfilePicLink((String) response.get("profile_picture_url"));

            return dto;

        } catch (Exception exp) {
            log.error("Instagram Profile fetching Failed: {}", exp.getMessage(), exp);
            log.error("Full stack trace:", exp);
            return null;
        }
    }
}