package com.ghouse.socialraven.service.post.text;

import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.repo.OAuthInfoRepo;
import com.ghouse.socialraven.repo.PostRepo;
import com.ghouse.socialraven.service.provider.XOAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class XTextPostPublisherService {

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private RestTemplate rest;

    @Autowired
    private OAuthInfoRepo oAuthInfoRepo;

    @Autowired
    private XOAuthService xOAuthService;

    private static final String X_API_BASE = "https://api.twitter.com/2";
    private static final int MAX_TWEET_LENGTH = 280;

    public void postTextToX(PostEntity post, OAuthInfoEntity authInfo) {
        try {
            // Validate input
            if (post == null) {
                throw new IllegalArgumentException("Post cannot be null");
            }

            // Get valid OAuth token
            OAuthInfoEntity validOAuthInfo = xOAuthService.getValidOAuthInfo(authInfo);
            String accessToken = validOAuthInfo.getAccessToken();

            log.info("=== X Text Post Started ===");
            log.info("PostID: {}, Title: {}", post.getId(), post.getTitle());

            // Build tweet text
            String tweetText = buildTweetText(post);
            log.debug("Tweet text ({}chars): {}", tweetText.length(), tweetText);

            // Create tweet
            createTweet(accessToken, tweetText);

            log.info("=== X Text Post Success ===");
            log.info("PostID: {} posted successfully", post.getId());

        } catch (IllegalArgumentException e) {
            log.error("Invalid input for X text post - PostID: {} - Error: {}",
                    post.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("=== X Text Post Failed ===");
            log.error("PostID: {}, Error: {}", post.getId(), e.getMessage(), e);
            throw new RuntimeException("X Text Post Failed: " + post.getId(), e);
        }
    }

    // CREATE TWEET
    private void createTweet(String accessToken, String tweetText) {
        String url = X_API_BASE + "/tweets";

        try {
            // Build request body
            Map<String, Object> body = new HashMap<>();
            body.put("text", tweetText);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Object> request = new HttpEntity<>(body, headers);

            log.debug("Sending tweet creation request to X API v2");
            ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("X tweet creation failed with status: " + response.getStatusCode());
            }

            // Extract tweet ID from response
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                String tweetId = (String) data.get("id");
                log.info("Tweet created successfully - TweetID: {}", tweetId);
            } else {
                log.info("Tweet created successfully - Response: {}", responseBody);
            }

        } catch (HttpClientErrorException e) {
            log.error("X API Error - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            // Handle specific error cases
            int statusCode = e.getStatusCode().value();
            String errorBody = e.getResponseBodyAsString();

            if (statusCode == 403) {
                if (errorBody.contains("duplicate")) {
                    throw new RuntimeException("Duplicate tweet detected. X doesn't allow posting the same content twice.", e);
                }
                throw new RuntimeException("X API forbidden - Check app permissions or user authorization", e);
            }

            if (statusCode == 429) {
                throw new RuntimeException("X API rate limit exceeded. Please try again later.", e);
            }

            throw new RuntimeException("X tweet creation failed: " + statusCode + " - " + errorBody, e);

        } catch (Exception e) {
            log.error("Failed to create tweet: {}", e.getMessage(), e);
            throw new RuntimeException("X tweet creation failed", e);
        }
    }

    // Helper method to build tweet text respecting X's character limit
    private String buildTweetText(PostEntity post) {
        StringBuilder text = new StringBuilder();

        // Add title if present
        if (post.getTitle() != null && !post.getTitle().trim().isEmpty()) {
            text.append(post.getTitle().trim());
        }

        // Add description if present
        if (post.getDescription() != null && !post.getDescription().trim().isEmpty()) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(post.getDescription().trim());
        }

        String result = text.toString();

        // Handle empty text
        if (result.trim().isEmpty()) {
            throw new IllegalArgumentException("Post must have title or description to create a tweet");
        }

        // Truncate to 280 characters if necessary
        if (result.length() > MAX_TWEET_LENGTH) {
            log.warn("Tweet text exceeds {} characters, truncating from {} to {}",
                    MAX_TWEET_LENGTH, result.length(), MAX_TWEET_LENGTH - 3);
            result = result.substring(0, MAX_TWEET_LENGTH - 3) + "...";
        }

        return result;
    }
}