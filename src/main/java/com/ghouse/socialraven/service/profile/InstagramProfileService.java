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
            log.debug("Access token (first 20 chars): {}...", accessToken.substring(0, Math.min(20, accessToken.length())));
            log.debug("Token expires at: {}, Current time: {}, Is expired: {}",
                    info.getExpiresAt(),
                    System.currentTimeMillis(),
                    info.getExpiresAt() < System.currentTimeMillis());

            // Simplified Instagram profile fetch
            String url = String.format(
                    "https://graph.facebook.com/v21.0/%s?fields=username,profile_picture_url&access_token=%s",
                    userId, accessToken
            );

            log.info("Making request to Instagram API...");

            ResponseEntity<Map> response = rest.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    Map.class
            );

            Map body = response.getBody();
            log.info("Instagram profile fetched successfully for user: {}", body.get("username"));

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(info.getProviderUserId());
            dto.setPlatform(Platform.instagram);
            dto.setUsername((String) body.get("username"));
            dto.setProfilePicLink((String) body.get("profile_picture_url"));

            return dto;

        } catch (Exception exp) {
            log.error("Instagram Profile fetching Failed: {}", exp.getMessage(), exp);
            log.error("Full error: ", exp);
            return null;
        }
    }
}