// com.ghouse.socialraven.service.oauth.XOAuthService
package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.dto.XOAuthCallbackRequest;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.util.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class XOAuthService {

    @Value("${x.client.id}")
    private String clientId;

    @Value("${x.client.secret}")
    private String clientSecret;

    @Value("${x.callback.uri}")
    private String callbackUri;
    // must equal NEXT_PUBLIC_X_REDIRECT_URI

    private final OAuthInfoRepo repo;
    private final RestTemplate rest = new RestTemplate();

    public ConnectedAccount handleCallback(XOAuthCallbackRequest req) {

        // 1) Exchange code for tokens
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", req.getCode());
        form.add("redirect_uri", callbackUri);
        form.add("client_id", clientId);
        form.add("code_verifier", req.getCodeVerifier());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // confidential client: send client_secret as Basic auth
        String basic = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret)
                        .getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + basic);

        HttpEntity<MultiValueMap<String, String>> entity =
                new HttpEntity<>(form, headers);

        ResponseEntity<Map> tokenResp = rest.postForEntity(
                "https://api.twitter.com/2/oauth2/token",
                entity,
                Map.class
        );

        if (!tokenResp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Token exchange failed: " + tokenResp);
        }

        Map body = tokenResp.getBody();
        String accessToken = (String) body.get("access_token");
        String refreshToken = (String) body.get("refresh_token");
        Integer expiresIn = (Integer) body.get("expires_in");

        OffsetDateTime expiresAtUtc = OffsetDateTime
                .now(ZoneOffset.UTC)
                .plusSeconds(expiresIn.longValue());
        long expiresAtMillis = expiresAtUtc.toInstant().toEpochMilli();

        // 2) Fetch X user profile with OAuth2 Bearer
        HttpHeaders profileHeaders = new HttpHeaders();
        profileHeaders.setBearerAuth(accessToken);

        HttpEntity<Void> profileEntity = new HttpEntity<>(profileHeaders);

        ResponseEntity<Map> profileResp = rest.exchange(
                "https://api.twitter.com/2/users/me?user.fields=profile_image_url,name",
                HttpMethod.GET,
                profileEntity,
                Map.class
        );

        if (!profileResp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Profile fetch failed: " + profileResp);
        }

        Map data = (Map) profileResp.getBody().get("data");
        String providerUserId = (String) data.get("id");
        String name = (String) data.get("name");
        String profileImageUrl = (String) data.get("profile_image_url");

        // 3) Persist in DB
        OAuthInfoEntity info = new OAuthInfoEntity();
        info.setProvider(Provider.X);
        info.setProviderUserId(providerUserId);
        info.setAccessToken(accessToken);
        info.setExpiresAt(expiresAtMillis);
        info.setExpiresAtUtc(expiresAtUtc);
        info.setUserId(req.getAppUserId());
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        info.setUserId(userId);

        AdditionalOAuthInfo add = new AdditionalOAuthInfo();
        add.setXRefreshToken(refreshToken);
        info.setAdditionalInfo(add);

        OAuthInfoEntity existingAuthInfo = repo.findByUserIdAndProviderAndProviderUserId(userId, Provider.X, info.getProviderUserId());
        if (existingAuthInfo != null) {
            throw new RuntimeException("X OAuth already exist");
        }

        repo.save(info);

        // 4) Return a DTO for frontend if needed
        ConnectedAccount dto = new ConnectedAccount();
        dto.setProviderUserId(info.getProviderUserId());
        dto.setPlatform(Platform.x);
        dto.setUsername(name);
        dto.setProfilePicLink(profileImageUrl);

        return dto;
    }

    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity info) {

        long now = System.currentTimeMillis();

        // 1. If token still valid → return it
        if (info.getExpiresAt() > now) {
            return info;
        }

        // 2. Expired → refresh
        return refreshAccessToken(info);
    }

    private OAuthInfoEntity refreshAccessToken(OAuthInfoEntity info) {

        String refreshToken = info.getAdditionalInfo().getXRefreshToken();
        if (refreshToken == null) {
            throw new RuntimeException("No X refresh token stored");
        }

        // Prepare form
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);

        // Create Basic auth header
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String basic = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret)
                        .getBytes(StandardCharsets.UTF_8));

        headers.set("Authorization", "Basic " + basic);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        // Call X refresh endpoint
        ResponseEntity<Map> resp = rest.postForEntity(
                "https://api.twitter.com/2/oauth2/token",
                entity,
                Map.class
        );

        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("X token refresh failed: " + resp);
        }

        Map body = resp.getBody();
        String newAccessToken = (String) body.get("access_token");
        String newRefreshToken = (String) body.get("refresh_token");
        Integer expiresIn = (Integer) body.get("expires_in");

        // Compute expiry time
        OffsetDateTime expiresAtUtc = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(expiresIn.longValue());
        long expiresAtMillis = expiresAtUtc.toInstant().toEpochMilli();

        // Update DB
        info.setAccessToken(newAccessToken);
        info.setExpiresAt(expiresAtMillis);
        info.setExpiresAtUtc(expiresAtUtc);

        if (newRefreshToken != null) {
            info.getAdditionalInfo().setXRefreshToken(newRefreshToken);
        }

        return repo.save(info);
    }


}
