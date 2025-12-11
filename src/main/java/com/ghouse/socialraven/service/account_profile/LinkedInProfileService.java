package com.ghouse.socialraven.service.account_profile;

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

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LinkedInProfileService {


    private final RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {

        try {

            String url = "https://api.linkedin.com/v2/userinfo";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(info.getAccessToken());

            ResponseEntity<Map> response = rest.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            Map body = response.getBody();

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(info.getProviderUserId());
            dto.setPlatform(Platform.linkedin);
            dto.setUsername((String) body.get("name"));
            dto.setProfilePicLink((String) body.get("picture"));  // already provided
            return dto;


        } catch (Exception exp) {
            log.error("LinkedIn Profile fetching Failed: {}", exp.getMessage(), exp);
            return null;
        }
    }

    /**
     * Extracts the best profile picture URL from LinkedIn playableStreams format.
     */
    private String extractProfilePic(Map profile) {
        try {
            Map picture = (Map) profile.get("profilePicture");
            Map displayImage = (Map) picture.get("displayImage~");
            List elements = (List) displayImage.get("elements");

            if (elements == null || elements.isEmpty()) return null;

            // Usually the highest resolution is the last element
            Map element = (Map) elements.get(elements.size() - 1);
            List identifiers = (List) element.get("identifiers");

            if (identifiers == null || identifiers.isEmpty()) return null;

            Map identifier = (Map) identifiers.get(0);
            return (String) identifier.get("identifier");

        } catch (Exception e) {
            return null;
        }
    }
}
