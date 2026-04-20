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

class InstagramWebhookServiceTest {

    @Test
    void processEventSchedulesWorkspaceScopedRefreshes() throws Exception {
        OAuthInfoRepo oauthInfoRepo = Mockito.mock(OAuthInfoRepo.class);
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        AnalyticsBackboneService analyticsBackboneService = Mockito.mock(AnalyticsBackboneService.class);
        InstagramWebhookService service = new InstagramWebhookService(
                oauthInfoRepo,
                postRepo,
                analyticsBackboneService
        );
        ReflectionTestUtils.setField(service, "appSecret", "test-secret");
        ReflectionTestUtils.setField(service, "verifyToken", "verify-me");

        OAuthInfoEntity workspaceOneConnection = new OAuthInfoEntity();
        workspaceOneConnection.setWorkspaceId("ws_1");
        workspaceOneConnection.setProvider(Provider.INSTAGRAM);
        workspaceOneConnection.setProviderUserId("ig_user_1");

        OAuthInfoEntity workspaceTwoConnection = new OAuthInfoEntity();
        workspaceTwoConnection.setWorkspaceId("ws_2");
        workspaceTwoConnection.setProvider(Provider.INSTAGRAM);
        workspaceTwoConnection.setProviderUserId("ig_user_1");

        PostEntity post = new PostEntity();
        post.setId(91L);

        when(oauthInfoRepo.findAllByProviderAndProviderUserId(Provider.INSTAGRAM, "ig_user_1"))
                .thenReturn(List.of(workspaceOneConnection, workspaceTwoConnection));
        when(postRepo.findAllByWorkspaceIdAndProviderAndProviderUserIdAndProviderPostId(
                "ws_1",
                Provider.INSTAGRAM,
                "ig_user_1",
                "17890001"
        )).thenReturn(List.of(post));
        when(postRepo.findAllByWorkspaceIdAndProviderAndProviderUserIdAndProviderPostId(
                "ws_2",
                Provider.INSTAGRAM,
                "ig_user_1",
                "17890001"
        )).thenReturn(List.of());

        String payload = """
                {
                  "entry": [
                    {
                      "id": "ig_user_1",
                      "changes": [
                        {
                          "field": "comments",
                          "value": {
                            "media_id": "17890001"
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
                Provider.INSTAGRAM,
                "ig_user_1",
                91L,
                "17890001"
        );
        verify(analyticsBackboneService).scheduleWebhookRefresh(
                "ws_2",
                Provider.INSTAGRAM,
                "ig_user_1",
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
