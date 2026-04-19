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
public class InstagramProfileService {

    private static final String GRAPH_BASE = "https://graph.instagram.com/v22.0";

    private RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {
        String accessToken = safeTrim(info.getAccessToken());
        String providerUserId = safeTrim(info.getProviderUserId());

        try {
            Map<String, Object> response = fetchProfileResponse(providerUserId, accessToken);
            if (response == null) {
                return null;
            }

            String username = firstNonBlank(response.get("username"), response.get("name"));
            if (username == null) {
                log.warn("Instagram profile response missing username for providerUserId={}", providerUserId);
                return null;
            }

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(providerUserId);
            dto.setPlatform(Platform.instagram);
            dto.setUsername(username);
            dto.setProfilePicLink(firstNonBlank(response.get("profile_pic"), response.get("profile_picture_url")));
            return dto;
        } catch (HttpClientErrorException exp) {
            log.warn("Instagram profile request failed: status={}, providerUserId={}, message={}",
                    exp.getStatusCode().value(),
                    providerUserId,
                    exp.getStatusText());
            return null;
        } catch (Exception exp) {
            log.warn("Instagram profile request failed for providerUserId={}: {}",
                    providerUserId,
                    exp.getMessage());
            return null;
        }
    }

    private Map<String, Object> fetchProfileResponse(String providerUserId, String accessToken) {
        List<ProfileLookup> lookups = List.of(
                new ProfileLookup(GRAPH_BASE + "/" + providerUserId, "id,username,name,profile_pic"),
                new ProfileLookup(GRAPH_BASE + "/" + providerUserId, "id,username,name,profile_picture_url"),
                new ProfileLookup("https://graph.instagram.com/me", "id,username,name,profile_picture_url"),
                new ProfileLookup("https://graph.instagram.com/me", "id,username,name")
        );

        for (ProfileLookup lookup : lookups) {
            try {
                String url = UriComponentsBuilder
                        .fromHttpUrl(lookup.path())
                        .queryParam("fields", lookup.fields())
                        .queryParam("access_token", accessToken)
                        .build()
                        .encode()
                        .toUriString();

                Map<String, Object> response = rest.getForObject(url, Map.class);
                if (response == null) {
                    continue;
                }

                if (firstNonBlank(response.get("username"), response.get("name")) != null) {
                    return response;
                }
            } catch (HttpClientErrorException ex) {
                log.debug("Instagram profile lookup failed for path={} fields={}: {}",
                        lookup.path(), lookup.fields(), ex.getStatusCode().value());
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

    private record ProfileLookup(String path, String fields) {
    }
}
