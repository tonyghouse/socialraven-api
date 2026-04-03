package com.tonyghouse.socialraven.service.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.constant.PostActorType;
import com.tonyghouse.socialraven.constant.PostCollectionVersionEvent;
import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.dto.PostCollectionApprovalDiffItemResponse;
import com.tonyghouse.socialraven.dto.PostCollectionApprovalDiffResponse;
import com.tonyghouse.socialraven.dto.PostCollectionVersionResponse;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostCollectionVersionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.PostMediaEntity;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.PostCollectionVersionRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PostCollectionVersionService {

    private static final TypeReference<List<AccountSnapshot>> ACCOUNT_SNAPSHOT_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<MediaSnapshot>> MEDIA_SNAPSHOT_LIST_TYPE = new TypeReference<>() {};

    @Autowired
    private PostCollectionVersionRepo postCollectionVersionRepo;

    @Autowired
    private PostCollectionRepo postCollectionRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClerkUserService clerkUserService;

    public PostCollectionVersionEntity recordVersion(PostCollectionEntity collection,
                                                     PostCollectionVersionEvent event,
                                                     String actorUserId) {
        return recordVersion(collection, event, actorUserId, null, null, PostActorType.WORKSPACE_USER);
    }

    public PostCollectionVersionEntity recordVersion(PostCollectionEntity collection,
                                                     PostCollectionVersionEvent event,
                                                     String actorUserId,
                                                     String actorDisplayName,
                                                     String actorEmail,
                                                     PostActorType actorType) {
        if (collection == null || collection.getId() == null) {
            return null;
        }

        int nextVersionNumber = collection.getVersionSequence() + 1;
        collection.setVersionSequence(nextVersionNumber);
        postCollectionRepo.save(collection);

        PostCollectionVersionEntity entity = new PostCollectionVersionEntity();
        entity.setPostCollectionId(collection.getId());
        entity.setWorkspaceId(collection.getWorkspaceId());
        entity.setVersionNumber(nextVersionNumber);
        entity.setVersionEvent(event);
        entity.setActorType(actorType != null ? actorType : PostActorType.WORKSPACE_USER);
        entity.setActorUserId(normalize(actorUserId));
        entity.setActorDisplayName(normalize(actorDisplayName));
        entity.setActorEmail(normalizeEmail(actorEmail));
        entity.setReviewStatus(collection.getReviewStatus() != null ? collection.getReviewStatus() : PostReviewStatus.DRAFT);
        entity.setDraft(collection.isDraft());
        entity.setScheduledTime(collection.getScheduledTime());
        entity.setDescription(collection.getDescription() != null ? collection.getDescription() : "");
        entity.setPlatformConfigs(normalizeJson(collection.getPlatformConfigs()));
        entity.setTargetAccounts(writeJson(buildAccountSnapshots(collection.getPosts())));
        entity.setMediaFiles(writeJson(buildMediaSnapshots(collection.getMediaFiles())));
        entity.setCreatedAt(OffsetDateTime.now());

        return postCollectionVersionRepo.save(entity);
    }

    public List<PostCollectionVersionResponse> buildVersionHistory(Long collectionId) {
        if (collectionId == null) {
            return List.of();
        }

        return postCollectionVersionRepo.findAllByPostCollectionIdOrderByVersionNumberDesc(collectionId).stream()
                .map(this::toResponse)
                .toList();
    }

    public PostCollectionApprovalDiffResponse buildApprovedDiff(PostCollectionEntity collection) {
        if (collection == null || collection.getId() == null || collection.getLastApprovedVersionId() == null) {
            return null;
        }

        PostCollectionVersionEntity approvedVersion = postCollectionVersionRepo.findById(collection.getLastApprovedVersionId())
                .orElse(null);
        PostCollectionVersionEntity currentVersion = postCollectionVersionRepo.findFirstByPostCollectionIdOrderByVersionNumberDesc(collection.getId())
                .orElse(null);
        if (approvedVersion == null || currentVersion == null) {
            return null;
        }

        List<PostCollectionApprovalDiffItemResponse> changes = new ArrayList<>();
        if (!Objects.equals(approvedVersion.getDescription(), currentVersion.getDescription())) {
            changes.add(new PostCollectionApprovalDiffItemResponse(
                    "description",
                    "Caption",
                    "text",
                    emptyToPlaceholder(approvedVersion.getDescription()),
                    emptyToPlaceholder(currentVersion.getDescription())
            ));
        }
        if (!Objects.equals(approvedVersion.getScheduledTime(), currentVersion.getScheduledTime())) {
            changes.add(new PostCollectionApprovalDiffItemResponse(
                    "scheduledTime",
                    "Schedule",
                    "datetime",
                    formatDateTime(approvedVersion.getScheduledTime()),
                    formatDateTime(currentVersion.getScheduledTime())
            ));
        }
        if (!jsonEquals(approvedVersion.getTargetAccounts(), currentVersion.getTargetAccounts())) {
            changes.add(new PostCollectionApprovalDiffItemResponse(
                    "accounts",
                    "Target Accounts",
                    "list",
                    summarizeAccounts(readAccounts(approvedVersion.getTargetAccounts())),
                    summarizeAccounts(readAccounts(currentVersion.getTargetAccounts()))
            ));
        }
        if (!jsonEquals(approvedVersion.getMediaFiles(), currentVersion.getMediaFiles())) {
            changes.add(new PostCollectionApprovalDiffItemResponse(
                    "media",
                    "Media",
                    "list",
                    summarizeMedia(readMedia(approvedVersion.getMediaFiles())),
                    summarizeMedia(readMedia(currentVersion.getMediaFiles()))
            ));
        }
        if (!jsonEquals(approvedVersion.getPlatformConfigs(), currentVersion.getPlatformConfigs())) {
            changes.add(new PostCollectionApprovalDiffItemResponse(
                    "platformConfigs",
                    "Platform Settings",
                    "json",
                    summarizeJson(approvedVersion.getPlatformConfigs()),
                    summarizeJson(currentVersion.getPlatformConfigs())
            ));
        }

        return new PostCollectionApprovalDiffResponse(
                approvedVersion.getId(),
                approvedVersion.getVersionNumber(),
                currentVersion.getId(),
                currentVersion.getVersionNumber(),
                !changes.isEmpty(),
                changes
        );
    }

    private PostCollectionVersionResponse toResponse(PostCollectionVersionEntity entity) {
        return new PostCollectionVersionResponse(
                entity.getId(),
                entity.getVersionNumber(),
                entity.getVersionEvent().name(),
                entity.getActorType() != null ? entity.getActorType().name() : PostActorType.WORKSPACE_USER.name(),
                entity.getActorUserId(),
                resolveActorDisplayName(entity),
                entity.getCreatedAt(),
                entity.getReviewStatus() != null ? entity.getReviewStatus().name() : PostReviewStatus.DRAFT.name(),
                entity.isDraft(),
                entity.getScheduledTime()
        );
    }

    private String resolveActorDisplayName(PostCollectionVersionEntity entity) {
        if (entity.getActorType() == PostActorType.SYSTEM) {
            return "SocialRaven";
        }
        if (entity.getActorType() == PostActorType.CLIENT_REVIEWER) {
            if (entity.getActorDisplayName() != null && !entity.getActorDisplayName().isBlank()) {
                return entity.getActorDisplayName().trim();
            }
            if (entity.getActorEmail() != null && !entity.getActorEmail().isBlank()) {
                return entity.getActorEmail().trim();
            }
            return "Client reviewer";
        }
        if (entity.getActorDisplayName() != null && !entity.getActorDisplayName().isBlank()) {
            return entity.getActorDisplayName().trim();
        }
        if (entity.getActorUserId() == null || entity.getActorUserId().isBlank()) {
            return "Unknown actor";
        }
        ClerkUserService.UserProfile profile = clerkUserService.getUserProfile(entity.getActorUserId());
        if (profile == null) {
            return entity.getActorUserId();
        }
        String first = profile.firstName() != null ? profile.firstName().trim() : "";
        String last = profile.lastName() != null ? profile.lastName().trim() : "";
        String fullName = (first + " " + last).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        if (profile.email() != null && !profile.email().isBlank()) {
            return profile.email().trim();
        }
        return entity.getActorUserId();
    }

    private List<AccountSnapshot> buildAccountSnapshots(List<PostEntity> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return posts.stream()
                .filter(post -> post.getProvider() != null && post.getProviderUserId() != null)
                .map(post -> new AccountSnapshot(post.getProvider().name(), post.getProviderUserId()))
                .sorted(Comparator.comparing(AccountSnapshot::provider).thenComparing(AccountSnapshot::providerUserId))
                .toList();
    }

    private List<MediaSnapshot> buildMediaSnapshots(List<PostMediaEntity> mediaFiles) {
        if (mediaFiles == null || mediaFiles.isEmpty()) {
            return List.of();
        }
        return mediaFiles.stream()
                .filter(media -> media.getFileKey() != null)
                .map(media -> new MediaSnapshot(media.getFileKey(), media.getFileName(), media.getMimeType()))
                .sorted(Comparator.comparing(MediaSnapshot::fileKey))
                .toList();
    }

    private List<AccountSnapshot> readAccounts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<AccountSnapshot> snapshots = objectMapper.readValue(json, ACCOUNT_SNAPSHOT_LIST_TYPE);
            return snapshots != null ? snapshots : List.of();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse account snapshot JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private List<MediaSnapshot> readMedia(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<MediaSnapshot> snapshots = objectMapper.readValue(json, MEDIA_SNAPSHOT_LIST_TYPE);
            return snapshots != null ? snapshots : List.of();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse media snapshot JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private String summarizeAccounts(List<AccountSnapshot> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return "None";
        }
        return accounts.stream()
                .map(account -> toLabel(account.provider()) + " - " + account.providerUserId())
                .toList()
                .toString();
    }

    private String summarizeMedia(List<MediaSnapshot> media) {
        if (media == null || media.isEmpty()) {
            return "None";
        }
        return media.stream()
                .map(item -> item.fileName() != null && !item.fileName().isBlank() ? item.fileName() : item.fileKey())
                .toList()
                .toString();
    }

    private String summarizeJson(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    private boolean jsonEquals(String left, String right) {
        if (Objects.equals(normalizeJson(left), normalizeJson(right))) {
            return true;
        }
        if ((left == null || left.isBlank()) && (right == null || right.isBlank())) {
            return true;
        }
        try {
            JsonNode leftNode = left == null || left.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(left);
            JsonNode rightNode = right == null || right.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(right);
            return Objects.equals(leftNode, rightNode);
        } catch (JsonProcessingException e) {
            return Objects.equals(left, right);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value != null ? value : List.of());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to write post collection version JSON", e);
        }
    }

    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return value;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeEmail(String value) {
        String normalized = normalize(value);
        return normalized != null ? normalized.toLowerCase() : null;
    }

    private String emptyToPlaceholder(String value) {
        return value != null && !value.isBlank() ? value : "(empty)";
    }

    private String formatDateTime(OffsetDateTime value) {
        return value != null ? value.toString() : "Not scheduled";
    }

    private String toLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String lower = raw.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record AccountSnapshot(String provider, String providerUserId) {
    }

    private record MediaSnapshot(String fileKey, String fileName, String mimeType) {
    }
}
