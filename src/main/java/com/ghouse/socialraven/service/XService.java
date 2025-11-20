package com.ghouse.socialraven.service;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.entity.OAuthInfo;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.util.SecurityContextUtil;
import com.ghouse.socialraven.util.TwitterOAuth1Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class XService {
    @Value("${x.api.key}")
    private String apiKey;

    @Value("${x.api.secret}")
    private String apiSecret;

    @Value("${x.callback.uri}")
    private String callbackUri;

    @Autowired
    private OAuthInfoRepo repo;


    public Map<String, String> getXRequestToken() throws TwitterException {
        Twitter twitter = new TwitterFactory().getInstance();
        twitter.setOAuthConsumer(apiKey, apiSecret);

        RequestToken requestToken = twitter.getOAuthRequestToken(callbackUri);

        return Map.of(
                "oauth_token", requestToken.getToken(),
                "oauth_token_secret", requestToken.getTokenSecret(),
                "auth_url", requestToken.getAuthorizationURL()
        );
    }


    public String getXAccessToken(Map<String, String> body) throws TwitterException {

        String oauthToken = body.get("oauth_token");
        String oauthVerifier = body.get("oauth_verifier");
        String oauthTokenSecret = body.get("oauth_token_secret");

        if (oauthToken == null || oauthVerifier == null || oauthTokenSecret == null) {
            throw new IllegalArgumentException("Missing OAuth fields");
        }

        Twitter twitter = new TwitterFactory().getInstance();
        twitter.setOAuthConsumer(apiKey, apiSecret);

        // Exchange request token + secret for access token
        AccessToken accessToken = twitter.getOAuthAccessToken(
                new RequestToken(oauthToken, oauthTokenSecret),
                oauthVerifier
        );

        String token = accessToken.getToken();
        String secret = accessToken.getTokenSecret();
        String xUserId = String.valueOf(accessToken.getUserId());

        // Save to DB
        OAuthInfo oauthInfo = new OAuthInfo();
        oauthInfo.setProvider(Provider.X);
        oauthInfo.setAccessToken(token);
        // Set far-future expiry (Dec 31, 2099). X tokens won't expire until revoked
        oauthInfo.setExpiresAt(4102444799000L);
        oauthInfo.setProviderUserId(xUserId);

        AdditionalOAuthInfo additional = new AdditionalOAuthInfo();
        additional.setXAccessSecret(secret);
        additional.setXUserId(xUserId);
        oauthInfo.setAdditionalInfo(additional);

        oauthInfo.setUserId(SecurityContextUtil.getUserId(SecurityContextHolder.getContext()));
        repo.save(oauthInfo);

        return xUserId;

    }

    public void postTweet(String userId, String message) {
        try {

            OAuthInfo info = repo.findByUserIdAndProvider(userId, Provider.X);

            if (info == null) {
                throw new RuntimeException("Unable to find X AuthInfo for userId: " + userId);
            }

            String url = "https://api.twitter.com/2/tweets";

            Map<String, String> extraParams = new HashMap<>();
            // v2 tweet endpoint has no query params for text
            // we send JSON body, but signature must include nothing extra

            String accessSecret = info.getAdditionalInfo().getXAccessSecret();


            String authHeader = TwitterOAuth1Util.generateAuthorizationHeader(
                    apiKey,
                    apiSecret,
                    info.getAccessToken(),
                    accessSecret,
                    "POST",
                    url,
                    extraParams
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> payload = Map.of("text", message);

            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> resp = rest.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class
            );

            System.out.println(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
