package com.ghouse.socialraven.service.account_profile;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class YouTubeProfileService {

    private final RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {

        try {

            String url = "https://www.googleapis.com/youtube/v3/channels"
                    + "?part=snippet"
                    + "&id=" + info.getProviderUserId(); // channel ID

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(info.getAccessToken());

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> resp =
                    rest.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) resp.getBody().get("items");

            if (items == null || items.isEmpty()) {
                log.error("YouTube: No channel data found");
                return null;
            }

            Map<String, Object> snippet =
                    (Map<String, Object>) items.get(0).get("snippet");

            String title = (String) snippet.get("title");
            Map thumbnails = (Map) snippet.get("thumbnails");

            String profilePic = extractThumbnail(thumbnails);

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(info.getProviderUserId());
            dto.setPlatform(Platform.youtube);
            dto.setUsername(title);
            dto.setProfilePicLink(profilePic);

            return dto;

        } catch (Exception e) {
            log.error("YouTube Profile Fetch Failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private String extractThumbnail(Map thumbnails) {
        try {
            // Prefer high resolution thumbnail when available
            if (thumbnails.containsKey("high")) {
                return (String) ((Map) thumbnails.get("high")).get("url");
            }
            if (thumbnails.containsKey("medium")) {
                return (String) ((Map) thumbnails.get("medium")).get("url");
            }
            if (thumbnails.containsKey("default")) {
                return (String) ((Map) thumbnails.get("default")).get("url");
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
