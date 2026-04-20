package com.tonyghouse.socialraven.service.provider;

import com.tonyghouse.socialraven.constant.LinkedInApiConstants;
import com.tonyghouse.socialraven.constant.LinkedInAccountType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientConnectionSessionEntity;
import com.tonyghouse.socialraven.helper.RedisTokenExpirySaver;
import com.tonyghouse.socialraven.model.AdditionalOAuthInfo;
import com.tonyghouse.socialraven.service.clientconnect.OAuthConnectionPersistenceService;
import com.tonyghouse.socialraven.service.clientconnect.OAuthConnectionPersistenceService.PersistedConnection;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import com.tonyghouse.socialraven.util.TimeUtil;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class LinkedInOAuthService {

    private static final String TOKEN_URL = "https://www.linkedin.com/oauth/v2/accessToken";
    private static final String USER_INFO_URL = "https://api.linkedin.com/v2/userinfo";
    private static final String ORGANIZATION_ACLS_URL = "https://api.linkedin.com/rest/organizationAcls";
    private static final String ORGANIZATIONS_URL = "https://api.linkedin.com/rest/organizations/";

    @Value("${linkedin.client.id}")
    private String clientId;

    @Value("${linkedin.client.secret}")
    private String clientSecret;

    @Value("${linkedin.redirect.uri}")
    private String redirectUri;

    @Autowired
    private RedisTokenExpirySaver redisTokenExpirySaver;

    @Autowired
    private OAuthConnectionPersistenceService oauthConnectionPersistenceService;

    private final RestTemplate restTemplate = new RestTemplate();

    public void exchangeCodeForToken(String code) {
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        String workspaceId = WorkspaceContext.getWorkspaceId();
        exchangeCode(code, workspaceId, userId, null, null, null);
    }

    public PersistedConnection exchangeCodeForClientConnection(String code,
                                                               WorkspaceClientConnectionSessionEntity session,
                                                               String ownerDisplayName,
                                                               String ownerEmail) {
        return exchangeCode(
                code,
                session.getWorkspaceId(),
                session.getCreatedByUserId(),
                session.getId(),
                ownerDisplayName,
                ownerEmail
        );
    }

    private PersistedConnection exchangeCode(String code,
                                             String workspaceId,
                                             String managingUserId,
                                             String sessionId,
                                             String ownerDisplayName,
                                             String ownerEmail) {
        Map<String, Object> tokenResponse = exchangeAuthorizationCode(code);

        String accessToken = requireString(tokenResponse.get("access_token"), "LinkedIn access_token");
        Number expiresInValue = requireNumber(tokenResponse.get("expires_in"), "LinkedIn expires_in");
        long expiryMillis = System.currentTimeMillis() + (expiresInValue.longValue() * 1000L);
        OffsetDateTime expiresAtUtc = TimeUtil.toUTCOffsetDateTime(expiryMillis);

        Map<String, Object> userInfo = getUserInfo(accessToken);
        String linkedInMemberId = requireString(userInfo.get("sub"), "LinkedIn user sub");
        String linkedInMemberName = trimToNull(valueAsString(userInfo.get("name")));

        List<ResolvedLinkedInOrganization> organizations = fetchAdministeredOrganizations(accessToken);
        if (organizations.isEmpty()) {
            AdditionalOAuthInfo additionalInfo = baseLinkedInInfo(linkedInMemberId, linkedInMemberName);
            additionalInfo.setLinkedinAccountType(LinkedInAccountType.MEMBER);
            return persistConnection(
                    workspaceId,
                    managingUserId,
                    sessionId,
                    ownerDisplayName,
                    ownerEmail,
                    linkedInMemberId,
                    accessToken,
                    expiresAtUtc,
                    additionalInfo
            );
        }

        PersistedConnection firstPersisted = null;
        for (ResolvedLinkedInOrganization organization : organizations) {
            AdditionalOAuthInfo additionalInfo = baseLinkedInInfo(linkedInMemberId, linkedInMemberName);
            additionalInfo.setLinkedinAccountType(LinkedInAccountType.ORGANIZATION);
            additionalInfo.setLinkedinOrganizationUrn(organization.organizationUrn());
            additionalInfo.setLinkedinOrganizationName(organization.name());
            additionalInfo.setLinkedinOrganizationVanityName(organization.vanityName());
            additionalInfo.setLinkedinOrganizationLogoUrl(organization.logoUrl());

            PersistedConnection persisted = persistConnection(
                    workspaceId,
                    managingUserId,
                    sessionId,
                    ownerDisplayName,
                    ownerEmail,
                    organization.organizationUrn(),
                    accessToken,
                    expiresAtUtc,
                    additionalInfo
            );
            if (firstPersisted == null) {
                firstPersisted = persisted;
            }
        }

        return firstPersisted;
    }

    private PersistedConnection persistConnection(String workspaceId,
                                                  String managingUserId,
                                                  String sessionId,
                                                  String ownerDisplayName,
                                                  String ownerEmail,
                                                  String providerUserId,
                                                  String accessToken,
                                                  OffsetDateTime expiresAtUtc,
                                                  AdditionalOAuthInfo additionalInfo) {
        if (sessionId != null) {
            return oauthConnectionPersistenceService.saveClientConnection(
                    workspaceId,
                    managingUserId,
                    sessionId,
                    ownerDisplayName,
                    ownerEmail,
                    Provider.LINKEDIN,
                    providerUserId,
                    accessToken,
                    expiresAtUtc,
                    additionalInfo
            );
        }

        return oauthConnectionPersistenceService.saveWorkspaceMemberConnection(
                workspaceId,
                managingUserId,
                Provider.LINKEDIN,
                providerUserId,
                accessToken,
                expiresAtUtc,
                additionalInfo
        );
    }

    private Map<String, Object> exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                TOKEN_URL,
                new HttpEntity<>(params, headers),
                Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new RuntimeException("LinkedIn token response is empty");
        }
        return body;
    }

    private Map<String, Object> getUserInfo(String accessToken) {
        HttpHeaders headers = authorizedHeaders(accessToken);
        ResponseEntity<Map> response = restTemplate.exchange(
                USER_INFO_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new RuntimeException("LinkedIn userinfo response is empty");
        }
        return body;
    }

    private List<ResolvedLinkedInOrganization> fetchAdministeredOrganizations(String accessToken) {
        Set<String> organizationUrns = new LinkedHashSet<>();
        int start = 0;
        int count = 100;

        while (true) {
            String url = UriComponentsBuilder
                    .fromHttpUrl(ORGANIZATION_ACLS_URL)
                    .queryParam("q", "roleAssignee")
                    .queryParam("role", "ADMINISTRATOR")
                    .queryParam("state", "APPROVED")
                    .queryParam("count", count)
                    .queryParam("start", start)
                    .build()
                    .encode()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(authorizedHeaders(accessToken)),
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            List<Map<String, Object>> elements = extractElements(body);
            if (elements.isEmpty()) {
                break;
            }

            for (Map<String, Object> element : elements) {
                String organizationUrn = trimToNull(valueAsString(
                        element.getOrDefault("organizationTarget", element.get("organization"))
                ));
                if (organizationUrn != null) {
                    organizationUrns.add(organizationUrn);
                }
            }

            if (elements.size() < count) {
                break;
            }
            start += count;
        }

        List<ResolvedLinkedInOrganization> organizations = new ArrayList<>();
        for (String organizationUrn : organizationUrns) {
            ResolvedLinkedInOrganization organization = fetchOrganization(accessToken, organizationUrn);
            if (organization != null) {
                organizations.add(organization);
            }
        }
        return organizations;
    }

    private ResolvedLinkedInOrganization fetchOrganization(String accessToken, String organizationUrn) {
        String organizationId = extractIdFromUrn(organizationUrn);
        if (organizationId == null) {
            return null;
        }

        String url = ORGANIZATIONS_URL + organizationId;
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders(accessToken)),
                Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null) {
            return null;
        }

        String resolvedOrganizationUrn = trimToNull(valueAsString(body.get("$URN")));
        if (resolvedOrganizationUrn == null) {
            resolvedOrganizationUrn = LinkedInApiConstants.toOrganizationUrn(
                    valueAsString(body.get("id"))
            );
        }
        if (resolvedOrganizationUrn == null) {
            resolvedOrganizationUrn = organizationUrn;
        }

        String organizationName = trimToNull(valueAsString(
                body.getOrDefault("localizedName", body.get("vanityName"))
        ));

        return new ResolvedLinkedInOrganization(
                resolvedOrganizationUrn,
                organizationName,
                trimToNull(valueAsString(body.get("vanityName"))),
                extractLogoUrl(body.get("logoV2"))
        );
    }

    private HttpHeaders authorizedHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Linkedin-Version", LinkedInApiConstants.API_VERSION);
        headers.set("X-RestLi-Protocol-Version", LinkedInApiConstants.RESTLI_PROTOCOL_VERSION);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractElements(Map<String, Object> response) {
        Object elements = response != null ? response.get("elements") : null;
        if (!(elements instanceof List<?> rawElements)) {
            return List.of();
        }

        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Object rawElement : rawElements) {
            if (rawElement instanceof Map<?, ?> map) {
                mapped.add((Map<String, Object>) map);
            }
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    private String extractLogoUrl(Object rawLogo) {
        if (!(rawLogo instanceof Map<?, ?> logoMap)) {
            return null;
        }

        for (String key : List.of("cropped", "original")) {
            String value = trimToNull(valueAsString(logoMap.get(key)));
            if (value != null && value.startsWith("http")) {
                return value;
            }
        }

        Object displayImage = logoMap.get("displayImage~");
        if (displayImage instanceof Map<?, ?> displayImageMap) {
            Object elements = displayImageMap.get("elements");
            if (elements instanceof List<?> elementList) {
                for (int i = elementList.size() - 1; i >= 0; i--) {
                    Object element = elementList.get(i);
                    if (!(element instanceof Map<?, ?> elementMap)) {
                        continue;
                    }
                    Object identifiers = elementMap.get("identifiers");
                    if (!(identifiers instanceof List<?> identifierList)) {
                        continue;
                    }
                    for (Object identifier : identifierList) {
                        if (!(identifier instanceof Map<?, ?> identifierMap)) {
                            continue;
                        }
                        String candidate = trimToNull(valueAsString(identifierMap.get("identifier")));
                        if (candidate != null && candidate.startsWith("http")) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return null;
    }

    private AdditionalOAuthInfo baseLinkedInInfo(String linkedInMemberId, String linkedInMemberName) {
        AdditionalOAuthInfo additionalOAuthInfo = new AdditionalOAuthInfo();
        additionalOAuthInfo.setLinkedinMemberId(linkedInMemberId);
        additionalOAuthInfo.setLinkedinMemberName(linkedInMemberName);
        return additionalOAuthInfo;
    }

    private String extractIdFromUrn(String urn) {
        if (urn == null) {
            return null;
        }
        int separator = urn.lastIndexOf(':');
        if (separator < 0 || separator == urn.length() - 1) {
            return null;
        }
        return urn.substring(separator + 1);
    }

    private String requireString(Object value, String fieldName) {
        String resolved = trimToNull(valueAsString(value));
        if (resolved == null) {
            throw new RuntimeException(fieldName + " is missing from LinkedIn response");
        }
        return resolved;
    }

    private Number requireNumber(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number;
        }
        throw new RuntimeException(fieldName + " is missing from LinkedIn response");
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public OAuthInfoEntity getValidOAuthInfo(OAuthInfoEntity info) {

        long now = System.currentTimeMillis();

        if (info.getExpiresAt() - now > 24 * 60 * 60 * 1000L) {
            return info;
        }

        return refreshAccessToken(info);
    }

    public OAuthInfoEntity refreshAccessToken(OAuthInfoEntity oAuthInfo) {
        redisTokenExpirySaver.saveTokenExpiry(oAuthInfo);
        return oAuthInfo;
    }

    private record ResolvedLinkedInOrganization(String organizationUrn,
                                                String name,
                                                String vanityName,
                                                String logoUrl) {
    }
}
