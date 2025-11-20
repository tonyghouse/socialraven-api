package com.ghouse.socialraven.service;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfo;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.util.SecurityContextUtil;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
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
import org.springframework.web.client.RestTemplate;


@Service
public class YouTubeService {

    @Value("${youtube.client.id}")
    private String clientId;

    @Value("${youtube.client.secret}")
    private String clientSecret;

    @Value("${youtube.redirect.uri}")
    private String redirectUri;

    @Autowired
    private OAuthInfoRepo repo;

    public void exchangeCodeForTokens(String code) {
        RestTemplate rest = new RestTemplate();

        // STEP 1: Exchange authorization code for tokens
        String tokenUrl = "https://oauth2.googleapis.com/token";

        Map<String, String> params = new HashMap<>();
        params.put("code", code);
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        params.put("redirect_uri", redirectUri);
        params.put("grant_type", "authorization_code");

        ResponseEntity<Map> resp = rest.postForEntity(tokenUrl, params, Map.class);

        String accessToken = (String) resp.getBody().get("access_token");
        String refreshToken = (String) resp.getBody().get("refresh_token");
        Integer expiresIn = (Integer) resp.getBody().get("expires_in");

        // STEP 2: Fetch YouTube Provider User ID (CHANNEL ID)
        String providerUserId = fetchYoutubeChannelId(accessToken);

        // STEP 3: Save in DB
        OAuthInfo oauthInfo = new OAuthInfo();
        oauthInfo.setProvider(Provider.YOUTUBE);
        oauthInfo.setProviderUserId(providerUserId);
        oauthInfo.setAccessToken(accessToken);
        oauthInfo.setExpiresAt(System.currentTimeMillis() + (expiresIn * 1000L));

        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        oauthInfo.setUserId(userId);

        AdditionalOAuthInfo additionalOAuthInfo = new AdditionalOAuthInfo();
        additionalOAuthInfo.setYoutubeRefreshToken(refreshToken);
        oauthInfo.setAdditionalInfo(additionalOAuthInfo);

        repo.save(oauthInfo);
    }

    // =====================
    // FETCH YOUTUBE CHANNEL ID
    // =====================
    private String fetchYoutubeChannelId(String accessToken) {
        RestTemplate rest = new RestTemplate();

        String url = "https://www.googleapis.com/youtube/v3/channels?part=id&mine=true";

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        org.springframework.http.HttpEntity<String> entity =
                new org.springframework.http.HttpEntity<>(headers);

        ResponseEntity<Map> response =
                rest.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);

        // Extract the first channel id
        var items = (java.util.List<Map<String, Object>>) response.getBody().get("items");
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("No YouTube channel found for the authenticated user");
        }

        Map<String, Object> firstItem = items.get(0);
        return (String) firstItem.get("id"); // The channel ID ✔
    }


    public void uploadVideo(String userId, File videoFile) throws Exception {

        OAuthInfo info = repo.findByUserIdAndProvider(userId, Provider.YOUTUBE);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(info.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> metadata = Map.of(
                "snippet", Map.of(
                        "title", "Uploaded via API",
                        "description", "This is an API upload"
                ),
                "status", Map.of(
                        "privacyStatus", "unlisted"
                )
        );

        // Step 1: Initiate resumable upload
        ResponseEntity<String> initRes = new RestTemplate().exchange(
                "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status",
                HttpMethod.POST,
                new HttpEntity<>(metadata, headers),
                String.class
        );

        String uploadUrl = initRes.getHeaders().get("Location").get(0);

        // Step 2: Upload video bytes
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setBearerAuth(info.getAccessToken());
        uploadHeaders.setContentLength(videoFile.length());
        uploadHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        byte[] data = Files.readAllBytes(videoFile.toPath());

        ResponseEntity<String> uploadRes = new RestTemplate().exchange(
                uploadUrl,
                HttpMethod.PUT,
                new HttpEntity<>(data, uploadHeaders),
                String.class
        );

        System.out.println(uploadRes.getBody());
    }

}

