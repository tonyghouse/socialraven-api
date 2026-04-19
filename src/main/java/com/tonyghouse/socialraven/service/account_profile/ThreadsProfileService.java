package com.tonyghouse.socialraven.service.account_profile;

import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class ThreadsProfileService {

    private static final String GRAPH_BASE = "https://graph.threads.net";

    private RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {
        String accessToken = safeTrim(info.getAccessToken());
        String providerUserId = safeTrim(info.getProviderUserId());

        try {
            Map<String, Object> response = fetchProfileResponse(accessToken);
            if (response == null) {
                return null;
            }

            String username = firstNonBlank(response.get("username"), response.get("name"));
            if (username == null) {
                log.warn("Threads profile response missing username for providerUserId={}", providerUserId);
                return null;
            }

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(providerUserId);
            dto.setPlatform(Platform.threads);
            dto.setUsername(username);
            dto.setProfilePicLink(firstNonBlank(
                    response.get("threads_profile_picture_url"),
                    response.get("profile_picture_url")
            ));
            return dto;
        } catch (HttpClientErrorException exp) {
            log.warn("Threads profile request failed: status={}, providerUserId={}, message={}",
                    exp.getStatusCode().value(),
                    providerUserId,
                    exp.getStatusText());
            return null;
        } catch (Exception exp) {
            log.warn("Threads profile request failed for providerUserId={}: {}",
                    providerUserId,
                    exp.getMessage());
            return null;
        }
    }

    private Map<String, Object> fetchProfileResponse(String accessToken) {
        List<String> candidateFields = List.of(
                "id,username,name,threads_profile_picture_url",
                "id,username,name,profile_picture_url",
                "id,username,name"
        );

        for (String fields : candidateFields) {
            try {
                String url = UriComponentsBuilder
                        .fromHttpUrl(GRAPH_BASE + "/me")
                        .queryParam("fields", fields)
                        .queryParam("access_token", accessToken)
                        .toUriString();

                Map<String, Object> response = rest.getForObject(url, Map.class);
                if (response == null) {
                    continue;
                }

                if (firstNonBlank(response.get("username"), response.get("name")) != null) {
                    return response;
                }
            } catch (HttpClientErrorException ex) {
                log.debug("Threads profile lookup failed for fields={}: {}", fields, ex.getStatusCode().value());
            }
        }

        return null;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }

            String stringValue = String.valueOf(value).trim();
            if (!stringValue.isEmpty()) {
                return stringValue;
            }
        }
        return null;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
