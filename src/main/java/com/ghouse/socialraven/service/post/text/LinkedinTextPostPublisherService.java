package com.ghouse.socialraven.service.post.text;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.repo.PostRepo;
import com.ghouse.socialraven.service.provider.LinkedInOAuthService;
import com.ghouse.socialraven.service.provider.XOAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LinkedinTextPostPublisherService {

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private RestTemplate rest;

    @Autowired
    private OAuthInfoRepo oAuthInfoRepo;

    @Autowired
    private LinkedInOAuthService linkedInOAuthService;

    @Autowired
    private XOAuthService xOAuthService;


    public void postTextToLinkedin(PostEntity post, OAuthInfoEntity authInfo) {
        try {
            OAuthInfoEntity validOAuthInfo = xOAuthService.getValidOAuthInfo(authInfo);
            String accessToken = validOAuthInfo.getAccessToken();

            // LinkedIn user ID stored from OAuth callback
            String personUrn = "urn:li:person:" + validOAuthInfo.getProviderUserId();

            // Build request body payload
            Map<String, Object> body = new HashMap<>();
            body.put("author", personUrn);
            body.put("lifecycleState", "PUBLISHED");

            Map<String, Object> shareContent = Map.of(
                    "shareCommentary", Map.of("text", post.getDescription()), // text content
                    "shareMediaCategory", "NONE"
            );

            body.put("specificContent", Map.of(
                    "com.linkedin.ugc.ShareContent", shareContent
            ));

            body.put("visibility", Map.of(
                    "com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"
            ));

            ObjectMapper objectMapper = new ObjectMapper();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-RestLi-Protocol-Version", "2.0.0"); // REQUIRED for LinkedIn

            // prevent conflicting Transfer-Encoding
            headers.setContentLength(objectMapper.writeValueAsBytes(body).length);

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(body), // send as raw string JSON
                    headers
            );

            ResponseEntity<String> response = rest.exchange(
                    "https://api.linkedin.com/v2/ugcPosts",
                    HttpMethod.POST,
                    entity,
                    String.class
            );


            log.info("LinkedIn Post Success → {}", response.getBody());
        } catch (Exception exp) {
            log.error("LinkedIn Text Post Failed → {}", exp.getMessage(), exp);
            throw new RuntimeException("LinkedIn Text Post Failed: "+post.getId(), exp);
        }
    }


}
