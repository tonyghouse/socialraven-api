package com.ghouse.socialraven.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfo;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.util.SecurityContextUtil;

import java.util.Map;
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
public class LinkedInService {

    @Value("${linkedin.client.id}")
    private String clientId;

    @Value("${linkedin.client.secret}")
    private String clientSecret;

    @Value("${linkedin.redirect.uri}")
    private String redirectUri;

    @Autowired
    private OAuthInfoRepo repo;

    public void exchangeCodeForToken(String code) {

        RestTemplate rest = new RestTemplate();

        String url = "https://www.linkedin.com/oauth/v2/accessToken";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> response = rest.postForEntity(
                url,
                new HttpEntity<>(params, headers),
                Map.class
        );


        String accessToken = (String) response.getBody().get("access_token");

        Integer expiresIn = (Integer) response.getBody().get("expires_in");

        // Fetch user info using OIDC endpoint
        Map<String, Object> userInfo = getUserInfo(accessToken);
        String linkedInUserId = (String) userInfo.get("sub");

        OAuthInfo oauthInfo = new OAuthInfo();
        oauthInfo.setProvider(Provider.LINKEDIN);
        oauthInfo.setProviderUserId(linkedInUserId);
        oauthInfo.setAccessToken(accessToken);
        oauthInfo.setExpiresAt(System.currentTimeMillis() + expiresIn * 1000L);
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        oauthInfo.setUserId(userId);
        AdditionalOAuthInfo additionalInfo = new AdditionalOAuthInfo(); //jsonb
        additionalInfo.setLinkedInUserId(linkedInUserId);
        oauthInfo.setAdditionalInfo(additionalInfo);


        repo.save(oauthInfo);
    }

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

    public void postToLinkedIn(String userId, String message) {
        OAuthInfo authInfo = repo.findByUserIdAndProvider(userId, Provider.LINKEDIN);
        if (authInfo == null) {
            throw new RuntimeException("Unable to find LinkedIn AuthInfo for userId: " + userId);
        }

        AdditionalOAuthInfo additionalInfo = authInfo.getAdditionalInfo();
        String linkedInUserId = additionalInfo.getLinkedInUserId();
        String author = "urn:li:person:" + linkedInUserId;

        String url = "https://api.linkedin.com/v2/ugcPosts";

        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> payload = Map.of(
                "author", author,
                "lifecycleState", "PUBLISHED",
                "specificContent", Map.of(
                        "com.linkedin.ugc.ShareContent", Map.of(
                                "shareCommentary", Map.of("text", message),
                                "shareMediaCategory", "NONE"
                        )
                ),
                "visibility", Map.of(
                        "com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"
                )
        );

        String jsonBody;
        try {
            jsonBody = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing payload", e);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authInfo.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, "application/json");

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        RestTemplate rt = new RestTemplate();
        ResponseEntity<String> response = rt.postForEntity(url, entity, String.class);

        System.out.println(response.getBody());
    }


}
