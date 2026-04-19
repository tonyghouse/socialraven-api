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

class ThreadsProfileServiceTest {

    @Test
    void fetchProfileFallsBackToAlternatePictureField() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        ThreadsProfileService service = new ThreadsProfileService();
        ReflectionTestUtils.setField(service, "rest", restTemplate);

        OAuthInfoEntity info = new OAuthInfoEntity();
        info.setProviderUserId("threads-user-1");
        info.setAccessToken("threads-token");

        server.expect(requestTo("https://graph.threads.net/me?fields=id,username,name,threads_profile_picture_url&access_token=threads-token"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"message":"Invalid field"}}
                                """));

        server.expect(requestTo("https://graph.threads.net/me?fields=id,username,name,profile_picture_url&access_token=threads-token"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "id": "threads-user-1",
                          "username": "threadscreator",
                          "profile_picture_url": "https://cdn.example.com/threads-avatar.jpg"
                        }
                        """, MediaType.APPLICATION_JSON));

        ConnectedAccount account = service.fetchProfile(info);

        assertThat(account).isNotNull();
        assertThat(account.getProviderUserId()).isEqualTo("threads-user-1");
        assertThat(account.getUsername()).isEqualTo("threadscreator");
        assertThat(account.getProfilePicLink()).isEqualTo("https://cdn.example.com/threads-avatar.jpg");
        server.verify();
    }
}
