package com.tonyghouse.socialraven.service.account_profile;

import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class ThreadsProfileService {

    private final RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {
        try {
            String accessToken = info.getAccessToken().trim();
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://graph.threads.net/me")
                    .queryParam("fields", "id,username,name,threads_profile_picture_url")
                    .queryParam("access_token", accessToken)
                    .toUriString();

            Map<String, Object> response = rest.getForObject(url, Map.class);
            if (response == null) {
                return null;
            }

            String username = response.get("username") != null
                    ? String.valueOf(response.get("username"))
                    : response.get("name") != null ? String.valueOf(response.get("name")) : null;

            if (username == null || username.isBlank()) {
                log.warn("Threads profile response missing username for providerUserId={}", info.getProviderUserId());
                return null;
            }

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(info.getProviderUserId());
            dto.setPlatform(Platform.threads);
            dto.setUsername(username);
            dto.setProfilePicLink(response.get("threads_profile_picture_url") != null
                    ? String.valueOf(response.get("threads_profile_picture_url"))
                    : null);
            return dto;
        } catch (HttpClientErrorException exp) {
            log.warn("Threads profile request failed: status={}, providerUserId={}, message={}",
                    exp.getStatusCode().value(),
                    info.getProviderUserId(),
                    exp.getStatusText());
            return null;
        } catch (Exception exp) {
            log.warn("Threads profile request failed for providerUserId={}: {}",
                    info.getProviderUserId(),
                    exp.getMessage());
            return null;
        }
    }
}
