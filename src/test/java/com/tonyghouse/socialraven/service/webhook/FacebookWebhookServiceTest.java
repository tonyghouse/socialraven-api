package com.tonyghouse.socialraven.service.webhook;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.service.analytics.AnalyticsBackboneService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class FacebookWebhookServiceTest {

    @Test
    void processEventSchedulesWorkspaceScopedRefreshes() throws Exception {
        OAuthInfoRepo oauthInfoRepo = Mockito.mock(OAuthInfoRepo.class);
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        AnalyticsBackboneService analyticsBackboneService = Mockito.mock(AnalyticsBackboneService.class);
        FacebookWebhookService service = new FacebookWebhookService(
                oauthInfoRepo,
                postRepo,
                analyticsBackboneService
        );
        ReflectionTestUtils.setField(service, "appSecret", "test-secret");
        ReflectionTestUtils.setField(service, "verifyToken", "verify-me");

        OAuthInfoEntity workspaceOneConnection = new OAuthInfoEntity();
        workspaceOneConnection.setWorkspaceId("ws_1");
        workspaceOneConnection.setProvider(Provider.FACEBOOK);
        workspaceOneConnection.setProviderUserId("page_123");

        OAuthInfoEntity workspaceTwoConnection = new OAuthInfoEntity();
        workspaceTwoConnection.setWorkspaceId("ws_2");
        workspaceTwoConnection.setProvider(Provider.FACEBOOK);
        workspaceTwoConnection.setProviderUserId("page_123");

        PostEntity post = new PostEntity();
        post.setId(91L);

        when(oauthInfoRepo.findAllByProviderAndProviderUserId(Provider.FACEBOOK, "page_123"))
                .thenReturn(List.of(workspaceOneConnection, workspaceTwoConnection));
        when(postRepo.findAllByWorkspaceIdAndProviderAndProviderUserIdAndProviderPostId(
                "ws_1",
                Provider.FACEBOOK,
                "page_123",
                "page_123_post_999"
        )).thenReturn(List.of(post));
        when(postRepo.findAllByWorkspaceIdAndProviderAndProviderUserIdAndProviderPostId(
                "ws_2",
                Provider.FACEBOOK,
                "page_123",
                "page_123_post_999"
        )).thenReturn(List.of());

        String payload = """
                {
                  "object": "page",
                  "entry": [
                    {
                      "id": "page_123",
                      "changes": [
                        {
                          "field": "feed",
                          "value": {
                            "post_id": "page_123_post_999"
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        service.processEvent(payload, signature("test-secret", payload));

        verify(analyticsBackboneService).scheduleWebhookRefresh(
                "ws_1",
                Provider.FACEBOOK,
                "page_123",
                91L,
                "page_123_post_999"
        );
        verify(analyticsBackboneService).scheduleWebhookRefresh(
                "ws_2",
                Provider.FACEBOOK,
                "page_123",
                null,
                null
        );
    }

    private String signature(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder("sha256=");
        for (byte current : digest) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }
}
