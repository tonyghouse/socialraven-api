package com.tonyghouse.socialraven.service.account_profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class TikTokProfileServiceTest {

    @Test
    void fetchProfilePrefersLargeAvatarUrl() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        TikTokProfileService service = new TikTokProfileService();
        ReflectionTestUtils.setField(service, "rest", restTemplate);

        OAuthInfoEntity info = new OAuthInfoEntity();
        info.setProviderUserId("tt-user-1");
        info.setAccessToken("tt-token");

        server.expect(requestTo("https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name,avatar_large_url,avatar_url_100,avatar_url"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer tt-token"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "user": {
                              "display_name": "TikTok Creator",
                              "avatar_url": "https://cdn.example.com/tiktok-avatar-small.jpg",
                              "avatar_url_100": "https://cdn.example.com/tiktok-avatar-100.jpg",
                              "avatar_large_url": "https://cdn.example.com/tiktok-avatar-large.jpg"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ConnectedAccount account = service.fetchProfile(info);

        assertThat(account).isNotNull();
        assertThat(account.getProviderUserId()).isEqualTo("tt-user-1");
        assertThat(account.getUsername()).isEqualTo("TikTok Creator");
        assertThat(account.getProfilePicLink()).isEqualTo("https://cdn.example.com/tiktok-avatar-large.jpg");
        server.verify();
    }
}
