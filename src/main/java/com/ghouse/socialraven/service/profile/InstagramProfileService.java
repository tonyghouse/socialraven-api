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

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class InstagramProfileService {

    private final RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {
        try {
            String accessToken = info.getAccessToken();
            
            log.info("Fetching Instagram profile via Facebook Graph API");
            log.info("Token length: {}, First 20 chars: {}", 
                accessToken.length(), 
                accessToken.substring(0, Math.min(20, accessToken.length())));

            // STEP 1: Get user's Facebook pages with Instagram accounts
            String pagesUrl = String.format(
                "https://graph.facebook.com/v21.0/me/accounts?fields=instagram_business_account{username,profile_picture_url}&access_token=%s",
                accessToken
            );

            log.info("Fetching Facebook pages with Instagram accounts");

            ResponseEntity<Map> pagesResponse = rest.exchange(
                pagesUrl,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                Map.class
            );

            Map pagesBody = pagesResponse.getBody();
            List<Map<String, Object>> pages = (List<Map<String, Object>>) pagesBody.get("data");

            if (pages == null || pages.isEmpty()) {
                log.error("No Facebook Pages found");
                return createFallbackProfile(info);
            }

            // STEP 2: Find page with Instagram account
            for (Map<String, Object> page : pages) {
                Map<String, Object> igAccount = (Map<String, Object>) page.get("instagram_business_account");
                
                if (igAccount != null) {
                    log.info("Found Instagram account: {}", igAccount.get("username"));
                    
                    ConnectedAccount dto = new ConnectedAccount();
                    dto.setProviderUserId(info.getProviderUserId());
                    dto.setPlatform(Platform.instagram);
                    dto.setUsername((String) igAccount.get("username"));
                    dto.setProfilePicLink((String) igAccount.get("profile_picture_url"));
                    
                    return dto;
                }
            }

            log.warn("No Instagram Business Account found on any page");
            return createFallbackProfile(info);

        } catch (Exception exp) {
            log.error("Instagram Profile fetching Failed: {}", exp.getMessage(), exp);
            return createFallbackProfile(info);
        }
    }

    private ConnectedAccount createFallbackProfile(OAuthInfoEntity info) {
        log.info("Creating fallback profile for Instagram user: {}", info.getProviderUserId());
        
        ConnectedAccount dto = new ConnectedAccount();
        dto.setProviderUserId(info.getProviderUserId());
        dto.setPlatform(Platform.instagram);
        dto.setUsername("Instagram User " + info.getProviderUserId().substring(0, 8));
        dto.setProfilePicLink(null);
        
        return dto;
    }
}