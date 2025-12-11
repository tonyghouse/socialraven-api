// com.ghouse.socialraven.service.oauth.XOAuthService
package com.ghouse.socialraven.service.provider;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.dto.XOAuthCallbackRequest;
import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.helper.RedisTokenExpirySaver;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.util.SecurityContextUtil;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private OAuthInfoRepo oAuthInfoRepo;

    @Autowired
    private RedisTokenExpirySaver redisTokenExpirySaver;

    public void handleCallback(XOAuthCallbackRequest req) {

        log.info("X OAuth with clientId:{} and CallBackUrl: {}", clientId, callbackUri);

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

        ResponseEntity<Map> tokenResp = restTemplate.postForEntity(
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

        ResponseEntity<Map> profileResp = restTemplate.exchange(
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

        OAuthInfoEntity existingAuthInfo = oAuthInfoRepo.findByUserIdAndProviderAndProviderUserId(userId, Provider.X, info.getProviderUserId());
        if (existingAuthInfo != null) {
            info.setId(existingAuthInfo.getId());
        }

        oAuthInfoRepo.save(info);
        redisTokenExpirySaver.saveTokenExpiry(info);
    }


    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity authInfo) {
        try {
            long now = System.currentTimeMillis();
            if (authInfo.getExpiresAt() - now > 24 * 60 * 60 * 1000L) {
                return authInfo;
            }

            // Check if refresh token exists
            if (StringUtils.isEmpty(authInfo.getAdditionalInfo().getXRefreshToken())) {
                throw new RuntimeException("No refresh token available. User needs to reconnect X account.");
            }

            // Token expired or about to expire, refresh it
            log.info("X access token expired or expiring soon, refreshing...");
            return refreshAccessToken(authInfo);

        } catch (Exception e) {
            log.error("Failed to get valid X OAuth info: {}", e.getMessage());
            throw new RuntimeException("Cannot obtain valid X access token: " + e.getMessage(), e);
        }
    }


    public OAuthInfoEntity refreshAccessToken(OAuthInfoEntity authInfo) {
        String url = "https://api.twitter.com/2/oauth2/token";

        try {
            log.info("Refreshing X access token for user: {}", authInfo.getProviderUserId());

            // X requires Basic Auth with client credentials
            String credentials = clientId + ":" + clientSecret;
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "Basic " + encodedCredentials);

            // Build form body
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "refresh_token");
            body.add("refresh_token", authInfo.getAdditionalInfo().getXRefreshToken());
            body.add("client_id", clientId);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            log.debug("Sending token refresh request to X");
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("X token refresh failed with status: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();

            // Extract tokens from response
            String newAccessToken = (String) responseBody.get("access_token");
            String newRefreshToken = (String) responseBody.get("refresh_token");
            Integer expiresIn = (Integer) responseBody.get("expires_in");

            if (newAccessToken == null) {
                throw new RuntimeException("X response missing access_token");
            }

            OffsetDateTime expiresAtUtc = OffsetDateTime.now(ZoneOffset.UTC)
                    .plusSeconds(expiresIn.longValue());
            long expiresAtMillis = expiresAtUtc.toInstant().toEpochMilli();

            // Update DB
            authInfo.setAccessToken(newAccessToken);
            authInfo.setExpiresAt(expiresAtMillis);
            authInfo.setExpiresAtUtc(expiresAtUtc);

            if (newRefreshToken != null) {
                authInfo.getAdditionalInfo().setXRefreshToken(newRefreshToken);
            }


            // Save updated tokens to database
            log.info("Successfully refreshed X access token");
            return oAuthInfoRepo.save(authInfo);

        } catch (HttpClientErrorException e) {
            log.error("X Token Refresh Failed - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            // Handle specific error cases
            if (e.getStatusCode().value() == 400) {
                String errorBody = e.getResponseBodyAsString();

                // Check if refresh token is invalid/expired
                if (errorBody.contains("invalid_request") || errorBody.contains("invalid_grant")) {
                    log.error("Refresh token is invalid or expired for user: {}",
                            authInfo.getProviderUserId());

                    // Mark the OAuth connection as invalid so user needs to reconnect
                    throw new RuntimeException("X refresh token expired. User needs to reconnect their X account.", e);
                }
            }

            throw new RuntimeException("Failed to refresh X access token: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error refreshing X token: {}", e.getMessage(), e);
            throw new RuntimeException("X token refresh failed", e);
        }
    }



}
