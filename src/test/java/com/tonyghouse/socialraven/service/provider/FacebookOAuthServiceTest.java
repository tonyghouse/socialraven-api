package com.tonyghouse.socialraven.service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
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

class FacebookOAuthServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void handleCallbackExchangesAndPersistsFacebookPageConnection() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OAuthConnectionPersistenceService persistenceService = mock(OAuthConnectionPersistenceService.class);

        FacebookOAuthService service = createService(
                restTemplate,
                persistenceService,
                mock(OAuthInfoRepo.class),
                mock(RedisTokenExpirySaver.class)
        );
        WorkspaceContext.set("workspace_123", WorkspaceRole.ADMIN);

        server.expect(requestTo("https://graph.facebook.com/v22.0/oauth/access_token?client_id=facebook-app-id&client_secret=facebook-app-secret&redirect_uri=https://app.socialraven.io/auth/facebook/callback&code=oauth-code"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"access_token":"short-lived-token","token_type":"bearer"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://graph.facebook.com/v22.0/oauth/access_token?grant_type=fb_exchange_token&client_id=facebook-app-id&client_secret=facebook-app-secret&fb_exchange_token=short-lived-token"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"access_token":"long-lived-token","token_type":"bearer","expires_in":5184000}
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://graph.facebook.com/v22.0/me?fields=id&access_token=long-lived-token"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"id":"facebook-user-123"}
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://graph.facebook.com/v22.0/me/accounts?fields=id,name,access_token,tasks&access_token=long-lived-token"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "id": "page-987",
                              "name": "Primary Test Page",
                              "access_token": "page-token",
                              "tasks": ["PROFILE_PLUS_CREATE_CONTENT"]
                            },
                            {
                              "id": "page-654",
                              "name": "Secondary Page",
                              "access_token": "other-page-token",
                              "tasks": ["ANALYZE"]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://graph.facebook.com/v22.0/page-987/subscribed_apps?subscribed_fields=feed"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"success":true}
                        """, MediaType.APPLICATION_JSON));

        service.handleCallback("oauth-code", "user_123");

        verify(persistenceService).saveWorkspaceMemberConnection(
                eq("workspace_123"),
                eq("user_123"),
                eq(Provider.FACEBOOK),
                eq("page-987"),
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

        FacebookOAuthService service = createService(
                restTemplate,
                mock(OAuthConnectionPersistenceService.class),
                oauthInfoRepo,
                redisTokenExpirySaver
        );

        OAuthInfoEntity oauthInfo = new OAuthInfoEntity();
        oauthInfo.setId(88L);
        oauthInfo.setAccessToken("stale-token");
        oauthInfo.setExpiresAt(0L);

        server.expect(requestTo("https://graph.facebook.com/v22.0/oauth/access_token?grant_type=fb_exchange_token&client_id=facebook-app-id&client_secret=facebook-app-secret&fb_exchange_token=stale-token"))
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

    private FacebookOAuthService createService(RestTemplate restTemplate,
                                               OAuthConnectionPersistenceService persistenceService,
                                               OAuthInfoRepo oauthInfoRepo,
                                               RedisTokenExpirySaver redisTokenExpirySaver) {
        FacebookOAuthService service = new FacebookOAuthService();
        ReflectionTestUtils.setField(service, "appId", "facebook-app-id");
        ReflectionTestUtils.setField(service, "appSecret", "facebook-app-secret");
        ReflectionTestUtils.setField(service, "redirectUri", "https://app.socialraven.io/auth/facebook/callback");
        ReflectionTestUtils.setField(service, "webhookSubscribedFields", "feed");
        ReflectionTestUtils.setField(service, "rest", restTemplate);
        ReflectionTestUtils.setField(service, "oauthConnectionPersistenceService", persistenceService);
        ReflectionTestUtils.setField(service, "repo", oauthInfoRepo);
        ReflectionTestUtils.setField(service, "redisTokenExpirySaver", redisTokenExpirySaver);
        return service;
    }
}
