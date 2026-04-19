package com.tonyghouse.socialraven.service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
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

class ThreadsOAuthServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void handleCallbackExchangesAndPersistsLongLivedToken() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OAuthConnectionPersistenceService persistenceService = mock(OAuthConnectionPersistenceService.class);

        ThreadsOAuthService service = createService(restTemplate, persistenceService, mock(OAuthInfoRepo.class), mock(RedisTokenExpirySaver.class));
        WorkspaceContext.set("workspace_123", WorkspaceRole.ADMIN);

        server.expect(requestTo("https://graph.threads.net/oauth/access_token"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=threads-app-id")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=threads-app-secret")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("redirect_uri=https%3A%2F%2Fapp.socialraven.io%2Fauth%2Fthreads%2Fcallback")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=oauth-code")))
                .andRespond(withSuccess("""
                        {"access_token":"short-lived-token","user_id":"threads-user-123"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://graph.threads.net/access_token?grant_type=th_exchange_token&client_secret=threads-app-secret&access_token=short-lived-token"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"access_token":"long-lived-token","expires_in":5184000}
                        """, MediaType.APPLICATION_JSON));

        service.handleCallback("oauth-code", "user_123");

        verify(persistenceService).saveWorkspaceMemberConnection(
                eq("workspace_123"),
                eq("user_123"),
                eq(Provider.THREADS),
                eq("threads-user-123"),
                eq("long-lived-token"),
                any(OffsetDateTime.class),
                any(AdditionalOAuthInfo.class)
        );
        server.verify();
    }

    @Test
    void refreshAccessTokenUpdatesAndPersistsOAuthInfo() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OAuthInfoRepo oauthInfoRepo = mock(OAuthInfoRepo.class);
        RedisTokenExpirySaver redisTokenExpirySaver = mock(RedisTokenExpirySaver.class);

        ThreadsOAuthService service = createService(restTemplate, mock(OAuthConnectionPersistenceService.class), oauthInfoRepo, redisTokenExpirySaver);

        OAuthInfoEntity oauthInfo = new OAuthInfoEntity();
        oauthInfo.setId(77L);
        oauthInfo.setAccessToken("stale-token");
        oauthInfo.setExpiresAt(0L);

        server.expect(requestTo("https://graph.threads.net/refresh_access_token?grant_type=th_refresh_token&access_token=stale-token"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"access_token":"refreshed-token","expires_in":5184000}
                        """, MediaType.APPLICATION_JSON));

        when(oauthInfoRepo.save(any(OAuthInfoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OAuthInfoEntity refreshed = service.refreshAccessToken(oauthInfo);

        assertThat(refreshed.getAccessToken()).isEqualTo("refreshed-token");
        assertThat(refreshed.getExpiresAt()).isGreaterThan(System.currentTimeMillis());
        assertThat(refreshed.getExpiresAtUtc()).isNotNull();
        verify(oauthInfoRepo).save(oauthInfo);
        verify(redisTokenExpirySaver).saveTokenExpiry(oauthInfo);
        server.verify();
    }

    private ThreadsOAuthService createService(RestTemplate restTemplate,
                                              OAuthConnectionPersistenceService persistenceService,
                                              OAuthInfoRepo oauthInfoRepo,
                                              RedisTokenExpirySaver redisTokenExpirySaver) {
        ThreadsOAuthService service = new ThreadsOAuthService();
        ReflectionTestUtils.setField(service, "appId", "threads-app-id");
        ReflectionTestUtils.setField(service, "appSecret", "threads-app-secret");
        ReflectionTestUtils.setField(service, "redirectUri", "https://app.socialraven.io/auth/threads/callback");
        ReflectionTestUtils.setField(service, "rest", restTemplate);
        ReflectionTestUtils.setField(service, "oauthConnectionPersistenceService", persistenceService);
        ReflectionTestUtils.setField(service, "repo", oauthInfoRepo);
        ReflectionTestUtils.setField(service, "redisTokenExpirySaver", redisTokenExpirySaver);
        return service;
    }
}
