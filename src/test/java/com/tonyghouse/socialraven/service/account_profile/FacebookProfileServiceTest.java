package com.tonyghouse.socialraven.service.account_profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
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

class FacebookProfileServiceTest {

    @Test
    void fetchProfileUsesResolvedPageAndPictureEdge() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        FacebookProfileService service = new FacebookProfileService();
        ReflectionTestUtils.setField(service, "rest", restTemplate);

        OAuthInfoEntity info = new OAuthInfoEntity();
        info.setProviderUserId("legacy-facebook-user-id");
        info.setAccessToken("user-token");

        server.expect(requestTo("https://graph.facebook.com/v22.0/me/accounts?fields=id,name,access_token,tasks&access_token=user-token"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "id": "page-1",
                              "name": "Facebook Test Page",
                              "access_token": "page-token",
                              "tasks": ["PROFILE_PLUS_CREATE_CONTENT"]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://graph.facebook.com/v22.0/page-1/picture?type=large&redirect=false&access_token=page-token"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "url": "https://cdn.example.com/facebook-avatar.jpg",
                            "is_silhouette": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ConnectedAccount account = service.fetchProfile(info);

        assertThat(account).isNotNull();
        assertThat(account.getProviderUserId()).isEqualTo("legacy-facebook-user-id");
        assertThat(account.getUsername()).isEqualTo("Facebook Test Page");
        assertThat(account.getProfilePicLink()).isEqualTo("https://cdn.example.com/facebook-avatar.jpg");
        server.verify();
    }
}
