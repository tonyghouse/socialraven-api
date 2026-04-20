package com.tonyghouse.socialraven.service.provider;

import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientConnectionSessionEntity;
import com.tonyghouse.socialraven.helper.RedisTokenExpirySaver;
import com.tonyghouse.socialraven.model.AdditionalOAuthInfo;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.service.clientconnect.OAuthConnectionPersistenceService;
import com.tonyghouse.socialraven.service.clientconnect.OAuthConnectionPersistenceService.PersistedConnection;
import com.tonyghouse.socialraven.util.TimeUtil;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class FacebookOAuthService {

    private static final Logger log = LoggerFactory.getLogger(FacebookOAuthService.class);
    private static final long REFRESH_WINDOW_MILLIS = 24L * 60L * 60L * 1000L;
    private static final String GRAPH_BASE = "https://graph.facebook.com/v22.0";

    @Value("${facebook.app.id}")
    private String appId;

    @Value("${facebook.app.secret}")
    private String appSecret;

    @Value("${facebook.redirect.uri}")
    private String redirectUri;

    @Value("${facebook.webhook.subscribed-fields:feed}")
    private String webhookSubscribedFields;

    @Autowired
    private OAuthInfoRepo repo;

    @Autowired
    private RestTemplate rest;

    @Autowired
    private RedisTokenExpirySaver redisTokenExpirySaver;

    @Autowired
    private OAuthConnectionPersistenceService oauthConnectionPersistenceService;

    public void handleCallback(String code, String userId) {
        handleCallback(code, userId, WorkspaceContext.getWorkspaceId(), null, null, null);
    }

    public PersistedConnection exchangeCodeForClientConnection(String code,
                                                               WorkspaceClientConnectionSessionEntity session,
                                                               String ownerDisplayName,
                                                               String ownerEmail) {
        return handleCallback(
                code,
                session.getCreatedByUserId(),
                session.getWorkspaceId(),
                session.getId(),
                ownerDisplayName,
                ownerEmail
        );
    }

    private PersistedConnection handleCallback(String code,
                                               String managingUserId,
                                               String workspaceId,
                                               String sessionId,
                                               String ownerDisplayName,
                                               String ownerEmail) {
        log.info("Starting Facebook OAuth callback for userId: {}", managingUserId);

        Map<String, Object> shortTokenResponse = exchangeCodeForShortLivedToken(code);
        String shortLivedAccessToken = requireString(
                shortTokenResponse.get("access_token"),
                "short-lived access token"
        );

        Map<String, Object> longTokenResponse = exchangeForLongLivedToken(shortLivedAccessToken);
        String longLivedAccessToken = requireString(
                longTokenResponse.get("access_token"),
                "long-lived access token"
        );
        Number expiresInValue = requireNumber(longTokenResponse.get("expires_in"), "expires_in");

        String facebookUserId = fetchFacebookUserId(longLivedAccessToken);
        ResolvedFacebookPage resolvedPage = resolveFacebookPage(longLivedAccessToken);
        subscribePageToWebhook(resolvedPage);
        long expiresAtMillis = System.currentTimeMillis() + expiresInValue.longValue() * 1000L;

        log.info("Resolved Facebook Page {} ({}) for Facebook user {}",
                resolvedPage.pageName(), resolvedPage.pageId(), facebookUserId);

        AdditionalOAuthInfo additionalOAuthInfo = new AdditionalOAuthInfo();
        if (sessionId != null) {
            return oauthConnectionPersistenceService.saveClientConnection(
                    workspaceId,
                    managingUserId,
                    sessionId,
                    ownerDisplayName,
                    ownerEmail,
                    Provider.FACEBOOK,
                    resolvedPage.pageId(),
                    longLivedAccessToken,
                    TimeUtil.toUTCOffsetDateTime(expiresAtMillis),
                    additionalOAuthInfo
            );
        }

        return oauthConnectionPersistenceService.saveWorkspaceMemberConnection(
                workspaceId,
                managingUserId,
                Provider.FACEBOOK,
                resolvedPage.pageId(),
                longLivedAccessToken,
                TimeUtil.toUTCOffsetDateTime(expiresAtMillis),
                additionalOAuthInfo
        );
    }

    public OAuthInfoEntity refreshAccessToken(OAuthInfoEntity info) {
        log.info("Refreshing Facebook long-lived token for OAuthInfo ID: {}", info.getId());

        Map<String, Object> response = exchangeForLongLivedToken(info.getAccessToken());
        String newAccessToken = requireString(response.get("access_token"), "refreshed access token");
        Number expiresInValue = requireNumber(response.get("expires_in"), "expires_in");
        long newExpiresAtMillis = System.currentTimeMillis() + expiresInValue.longValue() * 1000L;

        info.setAccessToken(newAccessToken);
        info.setExpiresAt(newExpiresAtMillis);
        info.setExpiresAtUtc(TimeUtil.toUTCOffsetDateTime(newExpiresAtMillis));

        OAuthInfoEntity saved = repo.save(info);
        redisTokenExpirySaver.saveTokenExpiry(saved);

        log.info("Facebook token refreshed successfully for OAuthInfo ID: {}", info.getId());
        return saved;
    }

    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity info) {
        long now = System.currentTimeMillis();
        if (info.getExpiresAt() - now > REFRESH_WINDOW_MILLIS) {
            return info;
        }
        return refreshAccessToken(info);
    }

    private Map<String, Object> exchangeCodeForShortLivedToken(String code) {
        String url = UriComponentsBuilder
                .fromHttpUrl(GRAPH_BASE + "/oauth/access_token")
                .queryParam("client_id", appId)
                .queryParam("client_secret", appSecret)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code", code)
                .build()
                .encode()
                .toUriString();

        Map<String, Object> response = rest.getForObject(url, Map.class);
        if (response == null) {
            throw new RuntimeException("Facebook short-lived token exchange returned an empty response");
        }
        return response;
    }

    private Map<String, Object> exchangeForLongLivedToken(String accessToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl(GRAPH_BASE + "/oauth/access_token")
                .queryParam("grant_type", "fb_exchange_token")
                .queryParam("client_id", appId)
                .queryParam("client_secret", appSecret)
                .queryParam("fb_exchange_token", accessToken)
                .build()
                .encode()
                .toUriString();

        Map<String, Object> response = rest.getForObject(url, Map.class);
        if (response == null) {
            throw new RuntimeException("Facebook long-lived token exchange returned an empty response");
        }
        return response;
    }

    private String fetchFacebookUserId(String accessToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl(GRAPH_BASE + "/me")
                .queryParam("fields", "id")
                .queryParam("access_token", accessToken)
                .build()
                .encode()
                .toUriString();

        Map<String, Object> response = rest.getForObject(url, Map.class);
        return requireString(response != null ? response.get("id") : null, "Facebook user id");
    }

    private ResolvedFacebookPage resolveFacebookPage(String accessToken) {
        List<ResolvedFacebookPage> pages = fetchFacebookPages(accessToken);
        if (pages.isEmpty()) {
            throw new RuntimeException("Facebook connection does not have any manageable Pages");
        }

        List<ResolvedFacebookPage> publishablePages = pages.stream()
                .filter(ResolvedFacebookPage::canPublishContent)
                .toList();
        if (!publishablePages.isEmpty()) {
            return publishablePages.get(0);
        }

        return pages.get(0);
    }

    private List<ResolvedFacebookPage> fetchFacebookPages(String accessToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl(GRAPH_BASE + "/me/accounts")
                .queryParam("fields", "id,name,access_token,tasks")
                .queryParam("access_token", accessToken)
                .build()
                .encode()
                .toUriString();

        Map<String, Object> response = rest.getForObject(url, Map.class);
        Object data = response != null ? response.get("data") : null;
        if (!(data instanceof List<?> pagesData)) {
            return List.of();
        }

        List<ResolvedFacebookPage> pages = new ArrayList<>();
        for (Object item : pagesData) {
            if (!(item instanceof Map<?, ?> rawPage)) {
                continue;
            }

            String pageId = valueAsString(rawPage.get("id"));
            String pageName = valueAsString(rawPage.get("name"));
            String pageAccessToken = valueAsString(rawPage.get("access_token"));
            if (pageId == null || pageAccessToken == null) {
                continue;
            }

            pages.add(new ResolvedFacebookPage(pageId, pageName, pageAccessToken, extractTasks(rawPage.get("tasks"))));
        }
        return pages;
    }

    private void subscribePageToWebhook(ResolvedFacebookPage page) {
        String subscribedFields = valueAsString(webhookSubscribedFields);
        if (subscribedFields == null) {
            throw new RuntimeException("Facebook webhook subscribed fields are not configured");
        }

        String url = UriComponentsBuilder
                .fromHttpUrl(GRAPH_BASE + "/" + page.pageId() + "/subscribed_apps")
                .queryParam("subscribed_fields", subscribedFields)
                .build()
                .encode()
                .toUriString();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", page.pageAccessToken());

        Map<String, Object> response = rest.postForObject(url, formEntity(form), Map.class);
        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            throw new RuntimeException("Facebook Page webhook subscription failed");
        }

        log.info("Facebook Page webhook subscription enabled for pageId={} fields={}",
                page.pageId(),
                subscribedFields);
    }

    private HttpEntity<MultiValueMap<String, String>> formEntity(MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return new HttpEntity<>(form, headers);
    }

    private List<String> extractTasks(Object rawTasks) {
        if (!(rawTasks instanceof List<?> taskList)) {
            return List.of();
        }

        List<String> tasks = new ArrayList<>(taskList.size());
        for (Object task : taskList) {
            if (task != null) {
                tasks.add(String.valueOf(task));
            }
        }
        return tasks;
    }

    private String requireString(Object value, String fieldName) {
        if (value == null) {
            throw new RuntimeException("Facebook response missing " + fieldName);
        }

        String stringValue = String.valueOf(value).trim();
        if (stringValue.isEmpty()) {
            throw new RuntimeException("Facebook response missing " + fieldName);
        }
        return stringValue;
    }

    private Number requireNumber(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number;
        }
        throw new RuntimeException("Facebook response missing " + fieldName);
    }

    private String valueAsString(Object value) {
        if (value == null) {
            return null;
        }

        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? null : stringValue;
    }

    private record ResolvedFacebookPage(String pageId,
                                        String pageName,
                                        String pageAccessToken,
                                        List<String> tasks) {

        private boolean canPublishContent() {
            return tasks == null
                    || tasks.isEmpty()
                    || tasks.contains("CREATE_CONTENT")
                    || tasks.contains("MANAGE")
                    || tasks.contains("MODERATE")
                    || tasks.contains("PROFILE_PLUS_CREATE_CONTENT")
                    || tasks.contains("PROFILE_PLUS_FULL_CONTROL")
                    || tasks.contains("PROFILE_PLUS_MANAGE");
        }
    }
}
