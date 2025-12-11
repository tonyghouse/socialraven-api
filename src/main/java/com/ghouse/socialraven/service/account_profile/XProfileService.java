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

import java.util.Map;

@Service
@Slf4j
public class XProfileService {

    private final RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {

        try {

            String url = "https://api.twitter.com/2/users/" + info.getProviderUserId()
                    + "?user.fields=profile_image_url,name";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(info.getAccessToken());

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = rest.exchange(url, HttpMethod.GET, entity, Map.class);

            Map data = (Map) response.getBody().get("data");

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(info.getProviderUserId());
            dto.setPlatform(Platform.x);
            dto.setUsername((String) data.get("name"));
            dto.setProfilePicLink((String) data.get("profile_image_url"));
            return dto;
        } catch (Exception exp) {
            log.error("X Profile fetching Failed: {}", exp.getMessage(), exp);
            return null;
        }
    }
}
