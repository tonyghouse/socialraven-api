package com.tonyghouse.socialraven.service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.helper.RedisTokenExpirySaver;
import com.tonyghouse.socialraven.model.AdditionalOAuthInfo;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.service.clientconnect.OAuthConnectionPersistenceService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class TikTokOAuthServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void handleCallbackExchangesAndPersistsTikTokTokens() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OAuthConnectionPersistenceService persistenceService = mock(OAuthConnectionPersistenceService.class);

        TikTokOAuthService service = createService(restTemplate, persistenceService, mock(OAuthInfoRepo.class), mock(RedisTokenExpirySaver.class));
        WorkspaceContext.set("workspace_123", WorkspaceRole.ADMIN);

        server.expect(requestTo("https://open.tiktokapis.com/v2/oauth/token/"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_key=tiktok-client-key")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=tiktok-client-secret")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=oauth-code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("redirect_uri=https%3A%2F%2Fsocialraven.io%2Fapi%2Fauth%2Ftiktok%2Fcallback")))
                .andRespond(withSuccess("""
                        {"access_token":"tik-access-token","refresh_token":"tik-refresh-token","open_id":"tik-open-id","scope":"user.info.basic,video.publish","expires_in":86400,"refresh_expires_in":31536000,"token_type":"Bearer"}
                        """, MediaType.APPLICATION_JSON));

        service.handleCallback("oauth-code", "user_123");

        verify(persistenceService).saveWorkspaceMemberConnection(
                eq("workspace_123"),
                eq("user_123"),
                eq(Provider.TIKTOK),
                eq("tik-open-id"),
                eq("tik-access-token"),
                any(OffsetDateTime.class),
                any(AdditionalOAuthInfo.class)
        );
        server.verify();
    }

    @Test
    void refreshAccessTokenUpdatesStoredRefreshToken() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OAuthInfoRepo oauthInfoRepo = mock(OAuthInfoRepo.class);
        RedisTokenExpirySaver redisTokenExpirySaver = mock(RedisTokenExpirySaver.class);

        TikTokOAuthService service = createService(restTemplate, mock(OAuthConnectionPersistenceService.class), oauthInfoRepo, redisTokenExpirySaver);

        OAuthInfoEntity oauthInfo = new OAuthInfoEntity();
        oauthInfo.setId(99L);
        oauthInfo.setAccessToken("old-access-token");
        oauthInfo.setExpiresAt(0L);
        AdditionalOAuthInfo additionalOAuthInfo = new AdditionalOAuthInfo();
        additionalOAuthInfo.setTiktokRefreshToken("old-refresh-token");
        oauthInfo.setAdditionalInfo(additionalOAuthInfo);

        server.expect(requestTo("https://open.tiktokapis.com/v2/oauth/token/"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("refresh_token=old-refresh-token")))
                .andRespond(withSuccess("""
                        {"access_token":"new-access-token","refresh_token":"new-refresh-token","open_id":"tik-open-id","scope":"user.info.basic,video.publish","expires_in":86400,"refresh_expires_in":31536000,"token_type":"Bearer"}
                        """, MediaType.APPLICATION_JSON));

        when(oauthInfoRepo.save(any(OAuthInfoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OAuthInfoEntity refreshed = service.refreshAccessToken(oauthInfo);

        assertThat(refreshed.getAccessToken()).isEqualTo("new-access-token");
        assertThat(refreshed.getAdditionalInfo().getTiktokRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(refreshed.getExpiresAt()).isGreaterThan(System.currentTimeMillis());
        verify(oauthInfoRepo).save(oauthInfo);
        verify(redisTokenExpirySaver).saveTokenExpiry(oauthInfo);
        server.verify();
    }

    private TikTokOAuthService createService(RestTemplate restTemplate,
                                             OAuthConnectionPersistenceService persistenceService,
                                             OAuthInfoRepo oauthInfoRepo,
                                             RedisTokenExpirySaver redisTokenExpirySaver) {
        TikTokOAuthService service = new TikTokOAuthService();
        ReflectionTestUtils.setField(service, "clientKey", "tiktok-client-key");
        ReflectionTestUtils.setField(service, "clientSecret", "tiktok-client-secret");
        ReflectionTestUtils.setField(service, "redirectUri", "https://socialraven.io/api/auth/tiktok/callback");
        ReflectionTestUtils.setField(service, "rest", restTemplate);
        ReflectionTestUtils.setField(service, "oauthConnectionPersistenceService", persistenceService);
        ReflectionTestUtils.setField(service, "repo", oauthInfoRepo);
        ReflectionTestUtils.setField(service, "redisTokenExpirySaver", redisTokenExpirySaver);
        return service;
    }
}
