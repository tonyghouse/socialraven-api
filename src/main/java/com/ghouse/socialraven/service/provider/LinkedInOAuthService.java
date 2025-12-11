package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.helper.RedisTokenExpirySaver;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.util.SecurityContextUtil;

import java.util.Map;

import com.ghouse.socialraven.util.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;


@Service
public class LinkedInOAuthService {

    @Value("${linkedin.client.id}")
    private String clientId;

    @Value("${linkedin.client.secret}")
    private String clientSecret;

    @Value("${linkedin.redirect.uri}")
    private String redirectUri;

    @Autowired
    private OAuthInfoRepo repo;

    @Autowired
    private RedisTokenExpirySaver redisTokenExpirySaver;

    private static final String TOKEN_URL = "https://www.linkedin.com/oauth/v2/accessToken";

    public void exchangeCodeForToken(String code) {

        RestTemplate rest = new RestTemplate();

        // Prepare params
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // Request
        ResponseEntity<Map> response = rest.postForEntity(
                TOKEN_URL,
                new HttpEntity<>(params, headers),
                Map.class
        );

        Map body = response.getBody();
        if (body == null) {
            throw new RuntimeException("LinkedIn token response is empty");
        }

        // Extract tokens
        String accessToken = (String) body.get("access_token");
        String refreshToken = (String) body.get("refresh_token");  // May be null if offline_access is missing
        Integer expiresIn = (Integer) body.get("expires_in");

        if (accessToken == null) {
            throw new RuntimeException("LinkedIn did not return access_token");
        }

        // Get LinkedIn UserInfo (OIDC)
        Map<String, Object> userInfo = getUserInfo(accessToken);
        String linkedInUserId = (String) userInfo.get("sub");

        // DB entity
        OAuthInfoEntity oauthInfoEntity = new OAuthInfoEntity();
        oauthInfoEntity.setProvider(Provider.LINKEDIN);
        oauthInfoEntity.setProviderUserId(linkedInUserId);
        oauthInfoEntity.setAccessToken(accessToken);

        long expiryMillis = System.currentTimeMillis() + (expiresIn * 1000L);
        oauthInfoEntity.setExpiresAt(expiryMillis);
        oauthInfoEntity.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(expiryMillis));

        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        oauthInfoEntity.setUserId(userId);

        // JSONB container
        AdditionalOAuthInfo additional = new AdditionalOAuthInfo();
        oauthInfoEntity.setAdditionalInfo(additional);

        OAuthInfoEntity existingAuthInfo = repo.findByUserIdAndProviderAndProviderUserId(userId, Provider.LINKEDIN, linkedInUserId);
        if (existingAuthInfo != null) {
            oauthInfoEntity.setId(existingAuthInfo.getId());
        }
        // Persist
        repo.save(oauthInfoEntity);
        redisTokenExpirySaver.saveTokenExpiry(oauthInfoEntity);
    }

    // Fetch LinkedIn OIDC User Info
    private Map<String, Object> getUserInfo(String accessToken) {
        RestTemplate rest = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map> resp = rest.exchange(
                "https://api.linkedin.com/v2/userinfo",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        return resp.getBody();
    }


    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity info) {

        long now = System.currentTimeMillis();

        // 1. If token still valid → return it
        if (info.getExpiresAt() - now > 24 * 60 * 60 * 1000L) {
            return info;
        }


        // 2. Expired → refresh
        return refreshAccessToken(info);
    }

    public OAuthInfoEntity refreshAccessToken(OAuthInfoEntity oAuthInfo) {
        //TODO-Implement refresh token from the linkedin accessToken (long lived accessToken of 60 days)

        redisTokenExpirySaver.saveTokenExpiry(oAuthInfo);
        return oAuthInfo;
    }


}
