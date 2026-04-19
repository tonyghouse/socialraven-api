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
public class TikTokProfileService {

    private final RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://open.tiktokapis.com/v2/user/info/")
                    .queryParam("fields", "open_id,display_name,avatar_url")
                    .toUriString();

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(info.getAccessToken().trim());
            org.springframework.http.HttpEntity<Void> request = new org.springframework.http.HttpEntity<>(headers);

            Map<String, Object> response = rest.exchange(url, org.springframework.http.HttpMethod.GET, request, Map.class)
                    .getBody();
            if (response == null) {
                return null;
            }

            Map<String, Object> data = asMap(response.get("data"));
            Map<String, Object> user = asMap(data != null ? data.get("user") : null);
            if (user == null) {
                return null;
            }

            String username = getNonBlank(user.get("display_name"));
            if (username == null) {
                log.warn("TikTok profile response missing display_name for providerUserId={}", info.getProviderUserId());
                return null;
            }

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(info.getProviderUserId());
            dto.setPlatform(Platform.tiktok);
            dto.setUsername(username);
            dto.setProfilePicLink(getNonBlank(user.get("avatar_url")));
            return dto;
        } catch (HttpClientErrorException exp) {
            log.warn("TikTok profile request failed: status={}, providerUserId={}, message={}",
                    exp.getStatusCode().value(),
                    info.getProviderUserId(),
                    exp.getStatusText());
            return null;
        } catch (Exception exp) {
            log.warn("TikTok profile request failed for providerUserId={}: {}",
                    info.getProviderUserId(),
                    exp.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private String getNonBlank(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? null : stringValue;
    }
}
