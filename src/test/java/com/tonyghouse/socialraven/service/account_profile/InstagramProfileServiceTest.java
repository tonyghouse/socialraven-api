package com.tonyghouse.socialraven.service.account_profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class InstagramProfileServiceTest {

    @Test
    void fetchProfileFallsBackToProfilePictureUrlLookup() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        InstagramProfileService service = new InstagramProfileService();
        ReflectionTestUtils.setField(service, "rest", restTemplate);

        OAuthInfoEntity info = new OAuthInfoEntity();
        info.setProviderUserId("ig-user-1");
        info.setAccessToken("ig-token");

        server.expect(requestTo("https://graph.instagram.com/v22.0/ig-user-1?fields=id,username,name,profile_pic&access_token=ig-token"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"message":"Invalid field"}}
                                """));

        server.expect(requestTo("https://graph.instagram.com/v22.0/ig-user-1?fields=id,username,name,profile_picture_url&access_token=ig-token"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "id": "ig-user-1",
                          "username": "igcreator",
                          "profile_picture_url": "https://cdn.example.com/instagram-avatar.jpg"
                        }
                        """, MediaType.APPLICATION_JSON));

        ConnectedAccount account = service.fetchProfile(info);

        assertThat(account).isNotNull();
        assertThat(account.getProviderUserId()).isEqualTo("ig-user-1");
        assertThat(account.getUsername()).isEqualTo("igcreator");
        assertThat(account.getProfilePicLink()).isEqualTo("https://cdn.example.com/instagram-avatar.jpg");
        server.verify();
    }
}
