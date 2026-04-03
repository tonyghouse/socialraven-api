package com.tonyghouse.socialraven.service.workspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.constant.PostCollectionType;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceLibraryItemStatus;
import com.tonyghouse.socialraven.constant.WorkspaceLibraryItemType;
import com.tonyghouse.socialraven.constant.WorkspaceLibrarySnippetTarget;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.workspace.UpsertWorkspaceLibraryBundleRequest;
import com.tonyghouse.socialraven.dto.workspace.UpsertWorkspaceLibraryItemRequest;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceLibraryBundleResponse;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceLibraryItemResponse;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceLibraryMediaRequest;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceLibraryMediaResponse;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceLibraryResponse;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.entity.WorkspaceLibraryBundleEntity;
import com.tonyghouse.socialraven.entity.WorkspaceLibraryBundleItemEntity;
import com.tonyghouse.socialraven.entity.WorkspaceLibraryItemEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.WorkspaceLibraryBundleItemRepo;
import com.tonyghouse.socialraven.repo.WorkspaceLibraryBundleRepo;
import com.tonyghouse.socialraven.repo.WorkspaceLibraryItemRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.storage.StorageService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceLibraryService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<LibraryMediaSnapshot>> MEDIA_LIST_TYPE = new TypeReference<>() {};
    private static final int MAX_TAG_COUNT = 20;

    @Autowired
    private WorkspaceLibraryItemRepo workspaceLibraryItemRepo;

    @Autowired
    private WorkspaceLibraryBundleRepo workspaceLibraryBundleRepo;

    @Autowired
    private WorkspaceLibraryBundleItemRepo workspaceLibraryBundleItemRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private WorkspaceCapabilityService workspaceCapabilityService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public WorkspaceLibraryResponse getWorkspaceLibrary(String userId, boolean approvedOnly) {
        String workspaceId = requireWorkspaceId();
        requireWorkspace(workspaceId);

        List<WorkspaceLibraryItemResponse> itemResponses = workspaceLibraryItemRepo
                .findAllByWorkspaceIdOrderByUpdatedAtDesc(workspaceId)
                .stream()
                .map(this::toItemResponse)
                .filter(item -> !approvedOnly || item.isUsable())
                .toList();

        Map<Long, WorkspaceLibraryItemResponse> itemsById = itemResponses.stream()
                .collect(Collectors.toMap(WorkspaceLibraryItemResponse::getId, item -> item, (left, right) -> left, LinkedHashMap::new));

        List<WorkspaceLibraryBundleResponse> bundleResponses = buildBundleResponses(
                workspaceLibraryBundleRepo.findAllByWorkspaceIdOrderByUpdatedAtDesc(workspaceId),
                itemsById,
                approvedOnly
        );

        return new WorkspaceLibraryResponse(itemResponses, bundleResponses);
    }

    @Transactional
    public WorkspaceLibraryItemResponse createLibraryItem(String userId, UpsertWorkspaceLibraryItemRequest request) {
        String workspaceId = requireWorkspaceId();
        WorkspaceRole role = requireWorkspaceRole();
        requireWorkspace(workspaceId);
        assertCanManageLibrary(workspaceId, userId, role);

        WorkspaceLibraryItemEntity entity = new WorkspaceLibraryItemEntity();
        entity.setWorkspaceId(workspaceId);
        applyItemRequest(entity, request, userId, true);
        return toItemResponse(workspaceLibraryItemRepo.save(entity));
    }

    @Transactional
    public WorkspaceLibraryItemResponse updateLibraryItem(String userId,
                                                          Long itemId,
                                                          UpsertWorkspaceLibraryItemRequest request) {
        String workspaceId = requireWorkspaceId();
        WorkspaceRole role = requireWorkspaceRole();
        requireWorkspace(workspaceId);
        assertCanManageLibrary(workspaceId, userId, role);

        WorkspaceLibraryItemEntity entity = workspaceLibraryItemRepo.findByIdAndWorkspaceId(itemId, workspaceId)
                .orElseThrow(() -> new SocialRavenException("Library item not found", HttpStatus.NOT_FOUND));
        applyItemRequest(entity, request, userId, false);
        return toItemResponse(workspaceLibraryItemRepo.save(entity));
    }

    @Transactional
    public void deleteLibraryItem(String userId, Long itemId) {
        String workspaceId = requireWorkspaceId();
        WorkspaceRole role = requireWorkspaceRole();
        requireWorkspace(workspaceId);
        assertCanManageLibrary(workspaceId, userId, role);

        WorkspaceLibraryItemEntity entity = workspaceLibraryItemRepo.findByIdAndWorkspaceId(itemId, workspaceId)
                .orElseThrow(() -> new SocialRavenException("Library item not found", HttpStatus.NOT_FOUND));
        workspaceLibraryItemRepo.delete(entity);
    }

    @Transactional
    public WorkspaceLibraryBundleResponse createLibraryBundle(String userId,
                                                              UpsertWorkspaceLibraryBundleRequest request) {
        String workspaceId = requireWorkspaceId();
        WorkspaceRole role = requireWorkspaceRole();
        requireWorkspace(workspaceId);
        assertCanManageLibrary(workspaceId, userId, role);

        WorkspaceLibraryBundleEntity entity = new WorkspaceLibraryBundleEntity();
        entity.setWorkspaceId(workspaceId);
        applyBundleRequest(entity, request, userId, true);
        WorkspaceLibraryBundleEntity saved = workspaceLibraryBundleRepo.save(entity);
        replaceBundleItems(saved.getId(), workspaceId, request != null ? request.getItemIds() : null);
        return buildBundleResponse(saved, false);
    }

    @Transactional
    public WorkspaceLibraryBundleResponse updateLibraryBundle(String userId,
                                                              Long bundleId,
                                                              UpsertWorkspaceLibraryBundleRequest request) {
        String workspaceId = requireWorkspaceId();
        WorkspaceRole role = requireWorkspaceRole();
        requireWorkspace(workspaceId);
        assertCanManageLibrary(workspaceId, userId, role);

        WorkspaceLibraryBundleEntity entity = workspaceLibraryBundleRepo.findByIdAndWorkspaceId(bundleId, workspaceId)
                .orElseThrow(() -> new SocialRavenException("Library bundle not found", HttpStatus.NOT_FOUND));
        applyBundleRequest(entity, request, userId, false);
        WorkspaceLibraryBundleEntity saved = workspaceLibraryBundleRepo.save(entity);
        replaceBundleItems(saved.getId(), workspaceId, request != null ? request.getItemIds() : null);
        return buildBundleResponse(saved, false);
    }

    @Transactional
    public void deleteLibraryBundle(String userId, Long bundleId) {
        String workspaceId = requireWorkspaceId();
        WorkspaceRole role = requireWorkspaceRole();
        requireWorkspace(workspaceId);
        assertCanManageLibrary(workspaceId, userId, role);

        WorkspaceLibraryBundleEntity entity = workspaceLibraryBundleRepo.findByIdAndWorkspaceId(bundleId, workspaceId)
                .orElseThrow(() -> new SocialRavenException("Library bundle not found", HttpStatus.NOT_FOUND));
        workspaceLibraryBundleRepo.delete(entity);
    }

    private void applyItemRequest(WorkspaceLibraryItemEntity entity,
                                  UpsertWorkspaceLibraryItemRequest request,
                                  String userId,
                                  boolean creating) {
        if (request == null) {
            throw new SocialRavenException("Library item payload is required", HttpStatus.BAD_REQUEST);
        }

        WorkspaceLibraryItemType itemType = parseItemType(request.getItemType());
        WorkspaceLibraryItemStatus status = parseStatus(request.getStatus());
        PostCollectionType postCollectionType = parseOptionalPostCollectionType(request.getPostCollectionType());
        WorkspaceLibrarySnippetTarget snippetTarget = parseOptionalSnippetTarget(request.getSnippetTarget());
        OffsetDateTime expiresAt = normalizeExpiry(request.getExpiresAt());
        List<String> tags = normalizeTags(request.getTags());
        List<LibraryMediaSnapshot> mediaSnapshots = normalizeMedia(request.getMedia());
        String normalizedName = requireNonBlank(request.getName(), "Library item name is required");
        String normalizedFolderName = normalizeOptionalText(request.getFolderName());
        String normalizedDescription = normalizeOptionalText(request.getDescription());
        String normalizedBody = normalizeOptionalText(request.getBody());
        String normalizedUsageNotes = normalizeOptionalText(request.getUsageNotes());
        String normalizedInternalNotes = normalizeOptionalText(request.getInternalNotes());
        String platformConfigsJson = writeJson(normalizePlatformConfigs(request.getPlatformConfigs()));

        validateItemPayload(
                itemType,
                postCollectionType,
                snippetTarget,
                normalizedBody,
                mediaSnapshots,
                platformConfigsJson
        );

        OffsetDateTime now = OffsetDateTime.now();
        entity.setItemType(itemType);
        entity.setStatus(status);
        entity.setName(normalizedName);
        entity.setFolderName(normalizedFolderName);
        entity.setDescription(normalizedDescription);
        entity.setBody(normalizedBody);
        entity.setSnippetTarget(snippetTarget);
        entity.setPostCollectionType(postCollectionType);
        entity.setTags(writeJson(tags));
        entity.setMediaFiles(writeJson(mediaSnapshots));
        entity.setPlatformConfigs(platformConfigsJson);
        entity.setUsageNotes(normalizedUsageNotes);
        entity.setInternalNotes(normalizedInternalNotes);
        entity.setExpiresAt(expiresAt);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(now);
        if (creating) {
            entity.setCreatedBy(userId);
            entity.setCreatedAt(now);
        }
    }

    private void validateItemPayload(WorkspaceLibraryItemType itemType,
                                     PostCollectionType postCollectionType,
                                     WorkspaceLibrarySnippetTarget snippetTarget,
                                     String body,
                                     List<LibraryMediaSnapshot> mediaSnapshots,
                                     String platformConfigsJson) {
        switch (itemType) {
            case MEDIA_ASSET -> {
                if (postCollectionType == null || postCollectionType == PostCollectionType.TEXT) {
                    throw new SocialRavenException(
                            "Media assets must target IMAGE or VIDEO content",
                            HttpStatus.BAD_REQUEST
                    );
                }
                if (mediaSnapshots.isEmpty()) {
                    throw new SocialRavenException("Media assets require at least one file", HttpStatus.BAD_REQUEST);
                }
                boolean invalidMimeType = mediaSnapshots.stream().anyMatch(media ->
                        postCollectionType == PostCollectionType.IMAGE
                                ? media.mimeType() == null || !media.mimeType().startsWith("image/")
                                : media.mimeType() == null || !media.mimeType().startsWith("video/")
                );
                if (invalidMimeType) {
                    throw new SocialRavenException(
                            "Asset media files must match the selected content type",
                            HttpStatus.BAD_REQUEST
                    );
                }
            }
            case SNIPPET -> {
                if (body == null || body.isBlank()) {
                    throw new SocialRavenException("Snippets require content", HttpStatus.BAD_REQUEST);
                }
                if (snippetTarget == null) {
                    throw new SocialRavenException("Snippets require a target", HttpStatus.BAD_REQUEST);
                }
                if (!mediaSnapshots.isEmpty()) {
                    throw new SocialRavenException("Snippets cannot contain media files", HttpStatus.BAD_REQUEST);
                }
            }
            case TEMPLATE -> {
                if (postCollectionType == null) {
                    throw new SocialRavenException("Templates require a content type", HttpStatus.BAD_REQUEST);
                }
                if ((body == null || body.isBlank())
                        && (platformConfigsJson == null || platformConfigsJson.isBlank())) {
                    throw new SocialRavenException(
                            "Templates must include caption content or platform settings",
                            HttpStatus.BAD_REQUEST
                    );
                }
                if (!mediaSnapshots.isEmpty()) {
                    throw new SocialRavenException("Templates cannot directly contain media files", HttpStatus.BAD_REQUEST);
                }
            }
        }
    }

    private void applyBundleRequest(WorkspaceLibraryBundleEntity entity,
                                    UpsertWorkspaceLibraryBundleRequest request,
                                    String userId,
                                    boolean creating) {
        if (request == null) {
            throw new SocialRavenException("Library bundle payload is required", HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now();
        entity.setName(requireNonBlank(request.getName(), "Bundle name is required"));
        entity.setDescription(normalizeOptionalText(request.getDescription()));
        entity.setCampaignLabel(normalizeOptionalText(request.getCampaignLabel()));
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(now);
        if (creating) {
            entity.setCreatedBy(userId);
            entity.setCreatedAt(now);
        }
    }

    private void replaceBundleItems(Long bundleId, String workspaceId, Collection<Long> requestedItemIds) {
        List<Long> normalizedItemIds = normalizeBundleItemIds(workspaceId, requestedItemIds);
        workspaceLibraryBundleItemRepo.deleteAllByBundleId(bundleId);
        List<WorkspaceLibraryBundleItemEntity> membershipEntities = new ArrayList<>();
        for (int index = 0; index < normalizedItemIds.size(); index++) {
            WorkspaceLibraryBundleItemEntity membershipEntity = new WorkspaceLibraryBundleItemEntity();
            membershipEntity.setBundleId(bundleId);
            membershipEntity.setLibraryItemId(normalizedItemIds.get(index));
            membershipEntity.setPosition(index);
            membershipEntities.add(membershipEntity);
        }
        if (!membershipEntities.isEmpty()) {
            workspaceLibraryBundleItemRepo.saveAll(membershipEntities);
        }
    }

    private List<Long> normalizeBundleItemIds(String workspaceId, Collection<Long> requestedItemIds) {
        if (requestedItemIds == null || requestedItemIds.isEmpty()) {
            throw new SocialRavenException("Bundles require at least one library item", HttpStatus.BAD_REQUEST);
        }

        Set<Long> normalizedIds = new LinkedHashSet<>();
        for (Long itemId : requestedItemIds) {
            if (itemId != null) {
                normalizedIds.add(itemId);
            }
        }
        if (normalizedIds.isEmpty()) {
            throw new SocialRavenException("Bundles require at least one library item", HttpStatus.BAD_REQUEST);
        }

        List<WorkspaceLibraryItemEntity> items = workspaceLibraryItemRepo.findAllById(normalizedIds);
        if (items.size() != normalizedIds.size()) {
            throw new SocialRavenException("One or more bundle items were not found", HttpStatus.BAD_REQUEST);
        }
        boolean invalidWorkspaceItem = items.stream().anyMatch(item -> !workspaceId.equals(item.getWorkspaceId()));
        if (invalidWorkspaceItem) {
            throw new SocialRavenException("Bundle items must belong to the active workspace", HttpStatus.BAD_REQUEST);
        }
        return List.copyOf(normalizedIds);
    }

    private List<WorkspaceLibraryBundleResponse> buildBundleResponses(List<WorkspaceLibraryBundleEntity> bundles,
                                                                      Map<Long, WorkspaceLibraryItemResponse> availableItemsById,
                                                                      boolean approvedOnly) {
        if (bundles.isEmpty()) {
            return List.of();
        }

        List<Long> bundleIds = bundles.stream().map(WorkspaceLibraryBundleEntity::getId).toList();
        Map<Long, List<WorkspaceLibraryBundleItemEntity>> membershipByBundleId =
                workspaceLibraryBundleItemRepo.findAllByBundleIdInOrderByPositionAscIdAsc(bundleIds).stream()
                        .collect(Collectors.groupingBy(
                                WorkspaceLibraryBundleItemEntity::getBundleId,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<WorkspaceLibraryBundleResponse> responses = new ArrayList<>();
        for (WorkspaceLibraryBundleEntity bundle : bundles) {
            List<WorkspaceLibraryBundleItemEntity> memberships = membershipByBundleId.getOrDefault(bundle.getId(), List.of());
            List<Long> itemIds = memberships.stream()
                    .map(WorkspaceLibraryBundleItemEntity::getLibraryItemId)
                    .toList();
            List<WorkspaceLibraryItemResponse> items = itemIds.stream()
                    .map(availableItemsById::get)
                    .filter(Objects::nonNull)
                    .toList();
            if (approvedOnly && items.isEmpty()) {
                continue;
            }
            responses.add(new WorkspaceLibraryBundleResponse(
                    bundle.getId(),
                    bundle.getName(),
                    bundle.getDescription(),
                    bundle.getCampaignLabel(),
                    itemIds,
                    items,
                    bundle.getCreatedAt(),
                    bundle.getUpdatedAt()
            ));
        }
        return responses;
    }

    private WorkspaceLibraryBundleResponse buildBundleResponse(WorkspaceLibraryBundleEntity bundle, boolean approvedOnly) {
        Map<Long, WorkspaceLibraryItemResponse> itemsById = workspaceLibraryItemRepo
                .findAllByWorkspaceIdOrderByUpdatedAtDesc(bundle.getWorkspaceId())
                .stream()
                .map(this::toItemResponse)
                .filter(item -> !approvedOnly || item.isUsable())
                .collect(Collectors.toMap(
                        WorkspaceLibraryItemResponse::getId,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return buildBundleResponses(List.of(bundle), itemsById, approvedOnly).stream()
                .findFirst()
                .orElseGet(() -> new WorkspaceLibraryBundleResponse(
                        bundle.getId(),
                        bundle.getName(),
                        bundle.getDescription(),
                        bundle.getCampaignLabel(),
                        List.of(),
                        List.of(),
                        bundle.getCreatedAt(),
                        bundle.getUpdatedAt()
                ));
    }

    @SuppressWarnings("unchecked")
    private WorkspaceLibraryItemResponse toItemResponse(WorkspaceLibraryItemEntity entity) {
        Map<String, Object> platformConfigs = null;
        if (entity.getPlatformConfigs() != null && !entity.getPlatformConfigs().isBlank()) {
            try {
                platformConfigs = objectMapper.readValue(entity.getPlatformConfigs(), Map.class);
            } catch (JsonProcessingException ignored) {
                platformConfigs = null;
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean expired = entity.getExpiresAt() != null && !entity.getExpiresAt().isAfter(now);
        boolean usable = entity.getStatus() == WorkspaceLibraryItemStatus.APPROVED && !expired;

        return new WorkspaceLibraryItemResponse(
                entity.getId(),
                entity.getItemType().name(),
                entity.getStatus().name(),
                entity.getName(),
                entity.getFolderName(),
                entity.getDescription(),
                entity.getBody(),
                entity.getSnippetTarget() != null ? entity.getSnippetTarget().name() : null,
                entity.getPostCollectionType() != null ? entity.getPostCollectionType().name() : null,
                readTags(entity.getTags()),
                readMedia(entity.getMediaFiles()).stream()
                        .map(this::toMediaResponse)
                        .toList(),
                platformConfigs,
                entity.getUsageNotes(),
                entity.getInternalNotes(),
                entity.getExpiresAt(),
                expired,
                usable,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private WorkspaceLibraryMediaResponse toMediaResponse(LibraryMediaSnapshot media) {
        String fileUrl = media.fileKey() != null && !media.fileKey().isBlank()
                ? storageService.generatePresignedGetUrl(media.fileKey(), Duration.ofMinutes(10))
                : null;
        return new WorkspaceLibraryMediaResponse(
                media.fileName(),
                media.mimeType(),
                media.fileKey(),
                media.size(),
                fileUrl
        );
    }

    private String requireWorkspaceId() {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new SocialRavenException("Workspace context is required", HttpStatus.FORBIDDEN);
        }
        return workspaceId;
    }

    private WorkspaceRole requireWorkspaceRole() {
        WorkspaceRole role = WorkspaceContext.getRole();
        if (role == null) {
            throw new SocialRavenException("Workspace role is required", HttpStatus.FORBIDDEN);
        }
        return role;
    }

    private WorkspaceEntity requireWorkspace(String workspaceId) {
        return workspaceRepo.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));
    }

    private void assertCanManageLibrary(String workspaceId, String userId, WorkspaceRole role) {
        if (!workspaceCapabilityService.hasCapability(
                workspaceId,
                userId,
                role,
                WorkspaceCapability.MANAGE_ASSET_LIBRARY
        )) {
            throw new SocialRavenException("Manage asset library capability is required", HttpStatus.FORBIDDEN);
        }
    }

    private WorkspaceLibraryItemType parseItemType(String value) {
        if (value == null || value.isBlank()) {
            throw new SocialRavenException("itemType is required", HttpStatus.BAD_REQUEST);
        }
        try {
            return WorkspaceLibraryItemType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SocialRavenException("Invalid itemType", HttpStatus.BAD_REQUEST);
        }
    }

    private WorkspaceLibraryItemStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return WorkspaceLibraryItemStatus.DRAFT;
        }
        try {
            return WorkspaceLibraryItemStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SocialRavenException("Invalid status", HttpStatus.BAD_REQUEST);
        }
    }

    private WorkspaceLibrarySnippetTarget parseOptionalSnippetTarget(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return WorkspaceLibrarySnippetTarget.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SocialRavenException("Invalid snippetTarget", HttpStatus.BAD_REQUEST);
        }
    }

    private PostCollectionType parseOptionalPostCollectionType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PostCollectionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SocialRavenException("Invalid postCollectionType", HttpStatus.BAD_REQUEST);
        }
    }

    private OffsetDateTime normalizeExpiry(OffsetDateTime expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        if (!expiresAt.isAfter(OffsetDateTime.now())) {
            throw new SocialRavenException("expiresAt must be in the future", HttpStatus.BAD_REQUEST);
        }
        return expiresAt;
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new SocialRavenException(message, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private List<String> normalizeTags(Collection<String> requestedTags) {
        if (requestedTags == null || requestedTags.isEmpty()) {
            return List.of();
        }

        Set<String> normalizedTags = new LinkedHashSet<>();
        for (String tag : requestedTags) {
            if (tag == null) {
                continue;
            }
            String normalizedTag = tag.trim();
            if (normalizedTag.isEmpty()) {
                continue;
            }
            if (normalizedTag.length() > 40) {
                throw new SocialRavenException("Tags must be 40 characters or fewer", HttpStatus.BAD_REQUEST);
            }
            normalizedTags.add(normalizedTag);
        }
        if (normalizedTags.size() > MAX_TAG_COUNT) {
            throw new SocialRavenException("A maximum of 20 tags is allowed", HttpStatus.BAD_REQUEST);
        }
        return List.copyOf(normalizedTags);
    }

    private List<LibraryMediaSnapshot> normalizeMedia(Collection<WorkspaceLibraryMediaRequest> mediaRequests) {
        if (mediaRequests == null || mediaRequests.isEmpty()) {
            return List.of();
        }

        List<LibraryMediaSnapshot> mediaSnapshots = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (WorkspaceLibraryMediaRequest mediaRequest : mediaRequests) {
            if (mediaRequest == null) {
                continue;
            }
            String fileKey = requireNonBlank(mediaRequest.getFileKey(), "Library media fileKey is required");
            if (!seenKeys.add(fileKey)) {
                continue;
            }
            mediaSnapshots.add(new LibraryMediaSnapshot(
                    requireNonBlank(mediaRequest.getFileName(), "Library media fileName is required"),
                    requireNonBlank(mediaRequest.getMimeType(), "Library media mimeType is required"),
                    fileKey,
                    mediaRequest.getSize()
            ));
        }
        return mediaSnapshots;
    }

    private Map<String, Object> normalizePlatformConfigs(Map<String, Object> platformConfigs) {
        if (platformConfigs == null || platformConfigs.isEmpty()) {
            return null;
        }
        return platformConfigs;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new SocialRavenException("Failed to serialize library data", HttpStatus.BAD_REQUEST);
        }
    }

    private List<String> readTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<LibraryMediaSnapshot> readMedia(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, MEDIA_LIST_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private record LibraryMediaSnapshot(String fileName, String mimeType, String fileKey, Long size) {
    }
}
