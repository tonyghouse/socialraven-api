package com.tonyghouse.socialraven.service.account_profile;

import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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

            log.debug("Fetching Instagram profile for providerUserId={}, tokenLength={}",
                    cleanUserId, cleanAccessToken.length());

            // **FIXED:** Use the correct Instagram Graph API domain (graph.instagram.com)
            // and fields that are guaranteed to be available (username and id).
            String url = "https://graph.instagram.com/me" +
                    "?fields=id,username" + // profile_picture_url is not a standard field here
                    "&access_token=" + cleanAccessToken;

            log.debug("Making API call to: {}", url.replace(cleanAccessToken, "***TOKEN***"));

            Map<String, Object> response = rest.getForObject(url, Map.class);

            if (response == null || response.get("username") == null) {
                log.warn("Instagram profile response missing username for providerUserId={}", cleanUserId);
                return null;
            }

            String fetchedUsername = (String) response.get("username");
            log.debug("Instagram profile fetched for providerUserId={}", cleanUserId);

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(cleanUserId);
            dto.setPlatform(Platform.instagram);
            dto.setUsername(fetchedUsername);

            // Note: We cannot reliably fetch the profile picture URL using this endpoint alone.
            // If the application needs a profile picture, a different approach (like a separate API request to a user picture endpoint, or potentially scraping with caution) would be needed, which is outside the scope of this fix.
            dto.setProfilePicLink(null);

            return dto;

        } catch (HttpClientErrorException exp) {
            log.warn("Instagram profile request failed: status={}, providerUserId={}, message={}",
                    exp.getStatusCode().value(),
                    info.getProviderUserId(),
                    exp.getStatusText());
            return null;
        } catch (Exception exp) {
            log.warn("Instagram profile request failed for providerUserId={}: {}",
                    info.getProviderUserId(),
                    exp.getMessage());
            return null;
        }
    }
}
