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

            log.info("=== DEBUGGING TOKEN FROM DATABASE ===");
            log.info("Provider User ID: {}", userId);
            log.info("Token is null: {}", accessToken == null);
            log.info("Token length: {}", accessToken != null ? accessToken.length() : "null");
            log.info("Token full value: {}", accessToken); // YES, LOG THE FULL TOKEN FOR NOW
            log.info("Token first 50 chars: {}", accessToken != null && accessToken.length() > 50
                    ? accessToken.substring(0, 50)
                    : accessToken);
            log.info("Token contains newlines: {}", accessToken != null && accessToken.contains("\n"));
            log.info("Token contains carriage returns: {}", accessToken != null && accessToken.contains("\r"));
            log.info("Token has leading spaces: {}", accessToken != null && !accessToken.equals(accessToken.trim()));
            log.info("=====================================");

            // Use the user_id directly to fetch basic profile
            String url = "https://graph.facebook.com/v21.0/" + userId +
                    "?fields=username,profile_picture_url" +
                    "&access_token=" + accessToken.trim(); // Trim it!

            log.info("Making API call to: {}", url.replace(accessToken.trim(), "***TOKEN***"));

            Map response = rest.getForObject(url, Map.class);

            if (response == null) {
                log.error("Empty response from Instagram API");
                return null;
            }

            log.info("SUCCESS! Instagram profile fetched: {}", response.get("username"));

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(userId);
            dto.setPlatform(Platform.instagram);
            dto.setUsername((String) response.get("username"));
            dto.setProfilePicLink((String) response.get("profile_picture_url"));

            return dto;

        } catch (Exception exp) {
            log.error("Instagram Profile fetching Failed: {}", exp.getMessage(), exp);
            return null;
        }
    }


}