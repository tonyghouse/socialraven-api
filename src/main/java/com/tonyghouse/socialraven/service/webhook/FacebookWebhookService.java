package com.tonyghouse.socialraven.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.service.analytics.AnalyticsBackboneService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookWebhookService {

    private final OAuthInfoRepo oauthInfoRepo;
    private final PostRepo postRepo;
    private final AnalyticsBackboneService analyticsBackboneService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${facebook.webhook.verify-token:}")
    private String verifyToken;

    @Value("${facebook.app.secret}")
    private String appSecret;

    public String verifySubscription(String mode, String token, String challenge) {
        if (!"subscribe".equals(mode) || !StringUtils.hasText(challenge)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Facebook webhook verification request");
        }
        if (!StringUtils.hasText(verifyToken) || !verifyToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid Facebook webhook verify token");
        }
        return challenge;
    }

    public void processEvent(String rawPayload, String signatureHeader) {
        validateSignature(rawPayload, signatureHeader);

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode entries = root.path("entry");
            if (!entries.isArray() || entries.isEmpty()) {
                return;
            }

            for (JsonNode entry : entries) {
                handleEntry(entry);
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to process Facebook webhook payload", exception);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Facebook webhook payload");
        }
    }

    private void handleEntry(JsonNode entry) {
        String pageId = textValue(entry.path("id"));
        if (!StringUtils.hasText(pageId)) {
            return;
        }

        List<OAuthInfoEntity> connections = oauthInfoRepo.findAllByProviderAndProviderUserId(
                Provider.FACEBOOK,
                pageId
        );
        if (connections.isEmpty()) {
            return;
        }

        Set<String> providerPostIds = extractProviderPostIds(entry);
        for (OAuthInfoEntity connection : connections) {
            scheduleRefreshes(connection, providerPostIds);
        }
    }

    private void scheduleRefreshes(OAuthInfoEntity connection, Set<String> providerPostIds) {
        String workspaceId = connection.getWorkspaceId();
        if (!StringUtils.hasText(workspaceId)) {
            return;
        }

        boolean scheduledSpecificPost = false;
        for (String providerPostId : providerPostIds) {
            List<PostEntity> posts = postRepo.findAllByWorkspaceIdAndProviderAndProviderUserIdAndProviderPostId(
                    workspaceId,
                    Provider.FACEBOOK,
                    connection.getProviderUserId(),
                    providerPostId
            );

            for (PostEntity post : posts) {
                analyticsBackboneService.scheduleWebhookRefresh(
                        workspaceId,
                        Provider.FACEBOOK,
                        connection.getProviderUserId(),
                        post.getId(),
                        providerPostId
                );
                scheduledSpecificPost = true;
            }
        }

        if (!scheduledSpecificPost) {
            analyticsBackboneService.scheduleWebhookRefresh(
                    workspaceId,
                    Provider.FACEBOOK,
                    connection.getProviderUserId(),
                    null,
                    null
            );
        }
    }

    private Set<String> extractProviderPostIds(JsonNode entry) {
        Set<String> providerPostIds = new LinkedHashSet<>();
        JsonNode changes = entry.path("changes");
        if (!changes.isArray()) {
            return providerPostIds;
        }

        for (JsonNode change : changes) {
            JsonNode value = change.path("value");
            addIfText(providerPostIds, value.path("post_id"));
            addIfText(providerPostIds, value.path("video_id"));
            addIfText(providerPostIds, value.path("photo_id"));
            addIfText(providerPostIds, value.path("object_id"));
            addIfText(providerPostIds, value.path("id"));
        }
        return providerPostIds;
    }

    private void addIfText(Set<String> values, JsonNode node) {
        String text = textValue(node);
        if (StringUtils.hasText(text)) {
            values.add(text);
        }
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateSignature(String rawPayload, String signatureHeader) {
        if (!StringUtils.hasText(signatureHeader) || !signatureHeader.startsWith("sha256=")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Facebook webhook signature");
        }
        if (!StringUtils.hasText(appSecret)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Facebook app secret not configured");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = hex(mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8)))
                    .getBytes(StandardCharsets.UTF_8);
            byte[] provided = signatureHeader.substring("sha256=".length()).getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, provided)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Facebook webhook signature");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Facebook webhook signature");
        }
    }

    private String hex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte current : value) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }
}
