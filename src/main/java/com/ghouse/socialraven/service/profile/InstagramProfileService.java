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

            // Always trim the token in case of whitespace issues from storage/retrieval
            final String cleanAccessToken = accessToken.trim();
            final String cleanUserId = userId.trim();

            log.info("=== DEBUGGING TOKEN FROM DATABASE (Trimmed) ===");
            log.info("Provider User ID: {}", cleanUserId);
            log.info("Token length: {}", cleanAccessToken.length());
            // It is generally bad practice to log full tokens in production. Use log level DEBUG if necessary.
            log.debug("Token full value: {}", cleanAccessToken);
            log.info("=====================================");

            // **FIXED:** Use the correct Instagram Graph API domain (graph.instagram.com)
            // and fields that are guaranteed to be available (username and id).
            String url = "https://graph.instagram.com/me" +
                    "?fields=id,username" + // profile_picture_url is not a standard field here
                    "&access_token=" + cleanAccessToken;

            log.info("Making API call to: {}", url.replace(cleanAccessToken, "***TOKEN***"));

            Map<String, Object> response = rest.getForObject(url, Map.class);

            if (response == null || response.get("username") == null) {
                log.error("Invalid or empty response from Instagram API, or missing username field.");
                return null;
            }

            String fetchedUsername = (String) response.get("username");
            log.info("SUCCESS! Instagram profile fetched: {}", fetchedUsername);

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(cleanUserId);
            dto.setPlatform(Platform.instagram);
            dto.setUsername(fetchedUsername);

            // Note: We cannot reliably fetch the profile picture URL using this endpoint alone.
            // If the application needs a profile picture, a different approach (like a separate API request to a user picture endpoint, or potentially scraping with caution) would be needed, which is outside the scope of this fix.
            dto.setProfilePicLink(null);

            return dto;

        } catch (Exception exp) {
            log.error("Instagram Profile fetching Failed: {}", exp.getMessage(), exp);
            return null;
        }
    }
}
