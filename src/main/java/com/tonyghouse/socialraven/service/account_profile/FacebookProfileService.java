package com.tonyghouse.socialraven.service.account_profile;

import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class FacebookProfileService {

    private static final String GRAPH_BASE = "https://graph.facebook.com/v22.0";

    private RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {
        String accessToken = safeTrim(info.getAccessToken());
        String providerUserId = safeTrim(info.getProviderUserId());

        try {
            ResolvedFacebookPage selectedPage = resolvePage(accessToken, providerUserId);
            if (selectedPage == null || !StringUtils.hasText(selectedPage.pageName())) {
                log.warn("Facebook profile response missing page name for providerUserId={}", providerUserId);
                return null;
            }

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(StringUtils.hasText(providerUserId) ? providerUserId : selectedPage.pageId());
            dto.setPlatform(Platform.facebook);
            dto.setUsername(selectedPage.pageName());
            dto.setProfilePicLink(fetchPictureUrl(selectedPage.pageId(), selectedPage.pageAccessToken()));
            return dto;
        } catch (HttpClientErrorException exp) {
            log.warn("Facebook profile request failed: status={}, providerUserId={}, message={}",
                    exp.getStatusCode().value(),
                    providerUserId,
                    exp.getStatusText());
            return null;
        } catch (Exception exp) {
            log.warn("Facebook profile request failed for providerUserId={}: {}",
                    providerUserId,
                    exp.getMessage());
            return null;
        }
    }

    private ResolvedFacebookPage resolvePage(String userAccessToken, String providerUserId) {
        List<ResolvedFacebookPage> pages = fetchManagedPages(userAccessToken);
        if (pages.isEmpty()) {
            return null;
        }

        for (ResolvedFacebookPage page : pages) {
            if (providerUserId.equals(page.pageId())) {
                return page;
            }
        }

        for (ResolvedFacebookPage page : pages) {
            if (page.canPublishContent()) {
                return page;
            }
        }

        return pages.get(0);
    }

    private List<ResolvedFacebookPage> fetchManagedPages(String userAccessToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl(GRAPH_BASE + "/me/accounts")
                .queryParam("fields", "id,name,access_token,tasks")
                .queryParam("access_token", userAccessToken)
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
            if (!StringUtils.hasText(pageId) || !StringUtils.hasText(pageAccessToken)) {
                continue;
            }

            pages.add(new ResolvedFacebookPage(
                    pageId,
                    pageName,
                    pageAccessToken,
                    extractTasks(rawPage.get("tasks"))
            ));
        }
        return pages;
    }

    private String fetchPictureUrl(String pageId, String pageAccessToken) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(GRAPH_BASE + "/" + pageId + "/picture")
                    .queryParam("type", "large")
                    .queryParam("redirect", "false")
                    .queryParam("access_token", pageAccessToken)
                    .build()
                    .encode()
                    .toUriString();

            Map<String, Object> response = rest.getForObject(url, Map.class);
            Map<String, Object> data = asMap(response != null ? response.get("data") : null);
            return valueAsString(data != null ? data.get("url") : null);
        } catch (HttpClientErrorException ex) {
            log.debug("Facebook picture lookup failed for pageId={}: {}", pageId, ex.getStatusCode().value());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
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

    private String valueAsString(Object value) {
        if (value == null) {
            return null;
        }

        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? null : stringValue;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
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
