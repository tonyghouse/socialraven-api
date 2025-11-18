package com.ghouse.socialraven.service;

import com.ghouse.socialraven.entity.OAuthToken;
import com.ghouse.socialraven.repo.OAuthTokenRepository;
import com.ghouse.socialraven.util.SecurityContextUtil;
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

import java.util.Map;

@Service
public class LinkedInService {

    @Value("${linkedin.client.id}")
    private String clientId;

    @Value("${linkedin.client.secret}")
    private String clientSecret;

    @Value("${linkedin.redirect.uri}")
    private String redirectUri;

    @Autowired
    private OAuthTokenRepository repo;

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

        OAuthToken token = new OAuthToken();
        token.setProvider("linkedin");
        token.setAccessToken(accessToken);
        token.setExpiresAt(System.currentTimeMillis() + expiresIn * 1000L);
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        token.setUserId(userId);

        repo.save(token);
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

    public void postToLinkedIn(Long userId, String message) {
        OAuthToken token = null;
//                repo.findByUserIdAndProvider(userId, "linkedin")
//                .orElseThrow();

        String author = null;
//                 "urn:li:person:" + token.getLinkedInUserId();

        String url = "https://api.linkedin.com/v2/ugcPosts";

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

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        new RestTemplate().postForEntity(url, new HttpEntity<>(payload, headers), String.class);
    }

}
