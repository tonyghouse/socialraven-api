package com.tonyghouse.socialraven.service.account_profile;

import com.tonyghouse.socialraven.constant.LinkedInAccountType;
import com.tonyghouse.socialraven.constant.LinkedInApiConstants;
import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.model.AdditionalOAuthInfo;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class LinkedInProfileService {

    private static final String USER_INFO_URL = "https://api.linkedin.com/v2/userinfo";

    private final RestTemplate rest = new RestTemplate();

    public ConnectedAccount fetchProfile(OAuthInfoEntity info) {
        AdditionalOAuthInfo additionalInfo = info.getAdditionalInfo();
        if (isOrganizationConnection(additionalInfo)) {
            return buildOrganizationProfile(info, additionalInfo);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(info.getAccessToken());
            headers.set("Linkedin-Version", LinkedInApiConstants.API_VERSION);
            headers.set("X-RestLi-Protocol-Version", LinkedInApiConstants.RESTLI_PROTOCOL_VERSION);

            ResponseEntity<Map> response = rest.exchange(
                    USER_INFO_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            Map body = response.getBody();
            if (body == null) {
                return buildFallbackProfile(info, additionalInfo);
            }

            ConnectedAccount dto = new ConnectedAccount();
            dto.setProviderUserId(info.getProviderUserId());
            dto.setPlatform(Platform.linkedin);
            dto.setUsername(resolveDisplayName((String) body.get("name"), info.getProviderUserId()));
            dto.setProfilePicLink((String) body.get("picture"));
            return dto;

        } catch (Exception exp) {
            log.warn("LinkedIn profile fetching failed for providerUserId={}: {}", info.getProviderUserId(), exp.getMessage());
            return buildFallbackProfile(info, additionalInfo);
        }
    }

    private ConnectedAccount buildOrganizationProfile(OAuthInfoEntity info, AdditionalOAuthInfo additionalInfo) {
        ConnectedAccount dto = new ConnectedAccount();
        dto.setProviderUserId(info.getProviderUserId());
        dto.setPlatform(Platform.linkedin);
        dto.setUsername(resolveDisplayName(
                additionalInfo.getLinkedinOrganizationName(),
                info.getProviderUserId()
        ));
        dto.setProfilePicLink(normalizeLogoUrl(additionalInfo.getLinkedinOrganizationLogoUrl()));
        return dto;
    }

    private ConnectedAccount buildFallbackProfile(OAuthInfoEntity info, AdditionalOAuthInfo additionalInfo) {
        ConnectedAccount dto = new ConnectedAccount();
        dto.setProviderUserId(info.getProviderUserId());
        dto.setPlatform(Platform.linkedin);

        if (isOrganizationConnection(additionalInfo)) {
            dto.setUsername(resolveDisplayName(
                    additionalInfo.getLinkedinOrganizationName(),
                    info.getProviderUserId()
            ));
            dto.setProfilePicLink(normalizeLogoUrl(additionalInfo.getLinkedinOrganizationLogoUrl()));
            return dto;
        }

        dto.setUsername(resolveDisplayName(
                additionalInfo != null ? additionalInfo.getLinkedinMemberName() : null,
                info.getProviderUserId()
        ));
        dto.setProfilePicLink(null);
        return dto;
    }

    private boolean isOrganizationConnection(AdditionalOAuthInfo additionalInfo) {
        return additionalInfo != null
                && LinkedInAccountType.ORGANIZATION.equals(additionalInfo.getLinkedinAccountType())
                && StringUtils.hasText(additionalInfo.getLinkedinOrganizationUrn());
    }

    private String normalizeLogoUrl(String value) {
        return value != null && value.startsWith("http") ? value : null;
    }

    private String resolveDisplayName(String candidate, String fallback) {
        return StringUtils.hasText(candidate) ? candidate : fallback;
    }
}
