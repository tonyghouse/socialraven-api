package com.ghouse.socialraven.service.post;

import com.ghouse.socialraven.constant.PostCollectionStatus;
import com.ghouse.socialraven.constant.PostCollectionType;
import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.constant.Provider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.dto.MediaResponse;
import com.ghouse.socialraven.dto.CalendarPostResponse;
import com.ghouse.socialraven.dto.PostCollection;
import com.ghouse.socialraven.dto.PostCollectionResponse;
import com.ghouse.socialraven.dto.PostMedia;
import com.ghouse.socialraven.dto.PostResponse;
import com.ghouse.socialraven.dto.ScheduleDraftRequest;
import com.ghouse.socialraven.dto.UpdatePostCollectionRequest;
import com.ghouse.socialraven.entity.PostCollectionEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.exception.SocialRavenException;
import com.ghouse.socialraven.helper.PostPoolHelper;
import com.ghouse.socialraven.mapper.PostTypeMapper;
import com.ghouse.socialraven.mapper.ProviderPlatformMapper;
import com.ghouse.socialraven.repo.PostCollectionRepo;
import com.ghouse.socialraven.repo.PostMediaRepo;
import com.ghouse.socialraven.repo.PostRepo;
import com.ghouse.socialraven.service.account_profile.AccountProfileService;
import com.ghouse.socialraven.service.storage.StorageService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import com.ghouse.socialraven.util.WorkspaceContext;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;


@Service
@Slf4j
public class PostService {

    @Autowired
    private PostCollectionRepo postCollectionRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private PostMediaRepo postMediaRepo;

    @Autowired
    private StorageService storageService;

    @Autowired
    private AccountProfileService accountProfileService;

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private ObjectMapper objectMapper;


    @Transactional
    public PostCollection schedulePostCollection(PostCollection postCollectionReq) {
        boolean isDraft = postCollectionReq.isDraft();
        List<ConnectedAccount> connectedAccounts = postCollectionReq.getConnectedAccounts();

        if (!isDraft && CollectionUtils.isEmpty(connectedAccounts)) {
            throw new RuntimeException("Select connected accounts for post collection");
        }

        PostCollectionEntity postCollection = new PostCollectionEntity();

        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        String workspaceId = WorkspaceContext.getWorkspaceId();

        postCollection.setPostCollectionStatus(isDraft ? PostCollectionStatus.DRAFT : PostCollectionStatus.SCHEDULED);
        PostCollectionType postType = postCollectionReq.getPostType();
        postCollection.setPostCollectionType(postType);
        postCollection.setCreatedBy(userId);
        postCollection.setWorkspaceId(workspaceId);
        postCollection.setTitle(postCollectionReq.getTitle() != null ? postCollectionReq.getTitle() : "");
        postCollection.setDescription(postCollectionReq.getDescription() != null ? postCollectionReq.getDescription() : "");
        OffsetDateTime scheduledTime = postCollectionReq.getScheduledTime();
        postCollection.setScheduledTime(scheduledTime);

        if (postCollectionReq.getPlatformConfigs() != null) {
            try {
                postCollection.setPlatformConfigs(objectMapper.writeValueAsString(postCollectionReq.getPlatformConfigs()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize platformConfigs", e);
            }
        }

        List<PostMedia> media = postCollectionReq.getMedia() != null ? postCollectionReq.getMedia() : Collections.emptyList();
        List<PostMediaEntity> postMediaEntities = new ArrayList<>();
        for (var postMediaDto : media) {
            PostMediaEntity postMediaEntity = new PostMediaEntity();
            postMediaEntity.setFileKey(postMediaDto.getFileKey());
            postMediaEntity.setSize(postMediaDto.getSize());
            postMediaEntity.setFileName(postMediaDto.getFileName());
            postMediaEntity.setMimeType(postMediaDto.getMimeType());
            postMediaEntity.setPostCollection(postCollection);
            postMediaEntities.add(postMediaEntity);
        }
        postCollection.setMediaFiles(postMediaEntities);

        List<PostEntity> postEntities = new ArrayList<>();
        if (!CollectionUtils.isEmpty(connectedAccounts)) {
            PostStatus postStatus = isDraft ? PostStatus.DRAFT : PostStatus.SCHEDULED;
            for (ConnectedAccount connectedAccount : connectedAccounts) {
                PostEntity post = new PostEntity();
                post.setProvider(ProviderPlatformMapper.getProviderByPlatform(connectedAccount.getPlatform()));
                post.setProviderUserId(connectedAccount.getProviderUserId());
                post.setPostCollection(postCollection);
                post.setPostStatus(postStatus);
                post.setPostType(PostTypeMapper.getPostTypeByPostCollectionType(postType));
                post.setScheduledTime(scheduledTime);
                postEntities.add(post);
            }
        }
        postCollection.setPosts(postEntities);

        PostCollectionEntity savedPost = postCollectionRepo.save(postCollection);

        if (!isDraft) {
            List<PostEntity> posts = savedPost.getPosts();
            for (PostEntity post : posts) {
                OffsetDateTime scheduleTime = post.getScheduledTime();
                long epochUtcMillis = scheduleTime.toInstant().toEpochMilli();
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.zadd(PostPoolHelper.getPostsPoolName(), epochUtcMillis, post.getId().toString());
                    log.info("Scheduled Post Added to Redis pool: postId={}, scheduleUTC={}", post.getId(), scheduleTime);
                }
            }
        } else {
            log.info("Draft saved: collectionId={}, workspaceId={}", savedPost.getId(), workspaceId);
        }

        return postCollectionReq;
    }

    @Transactional
    public PostCollectionResponse scheduleDraftCollection(String userId, Long collectionId, ScheduleDraftRequest req) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException(
                        "Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (collection.getPostCollectionStatus() != PostCollectionStatus.DRAFT) {
            throw new SocialRavenException("Collection is not a draft", HttpStatus.BAD_REQUEST);
        }
        if (CollectionUtils.isEmpty(collection.getPosts())) {
            throw new SocialRavenException(
                    "Select at least one connected account before scheduling", HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime scheduledTime = req.getScheduledTime();
        collection.setScheduledTime(scheduledTime);
        collection.setPostCollectionStatus(PostCollectionStatus.SCHEDULED);

        for (PostEntity post : collection.getPosts()) {
            post.setScheduledTime(scheduledTime);
            post.setPostStatus(PostStatus.SCHEDULED);
        }

        PostCollectionEntity saved = postCollectionRepo.save(collection);

        long epochUtcMillis = scheduledTime.toInstant().toEpochMilli();
        try (Jedis jedis = jedisPool.getResource()) {
            for (PostEntity post : saved.getPosts()) {
                jedis.zadd(PostPoolHelper.getPostsPoolName(), epochUtcMillis, post.getId().toString());
                log.info("Draft promoted to scheduled: postId={}, scheduleUTC={}", post.getId(), scheduledTime);
            }
        }

        List<ConnectedAccount> allConnectedAccounts = accountProfileService.getAllConnectedAccounts(workspaceId);
        Map<String, ConnectedAccount> connectedAccountMap = allConnectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));
        return getPostCollectionResponse(saved, connectedAccountMap);
    }

    @Transactional
    public void deletePostCollection(String userId, Long collectionId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException(
                        "Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        List<PostEntity> posts = collection.getPosts();
        if (posts != null && !posts.isEmpty()) {
            try (Jedis jedis = jedisPool.getResource()) {
                String[] postIds = posts.stream()
                        .map(p -> p.getId().toString())
                        .toArray(String[]::new);
                jedis.zrem(PostPoolHelper.getPostsPoolName(), postIds);
            }
        }
        postCollectionRepo.delete(collection);
        log.info("Deleted post collection id={} for workspaceId={}", collectionId, workspaceId);
    }

    @Transactional
    public PostCollectionResponse updatePostCollection(String userId, Long collectionId, UpdatePostCollectionRequest req) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException(
                        "Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }

        if (req.getTitle() != null) {
            collection.setTitle(req.getTitle());
        }
        if (req.getDescription() != null) {
            collection.setDescription(req.getDescription());
        }

        final OffsetDateTime scheduledTime = req.getScheduledTime() != null
                ? req.getScheduledTime()
                : collection.getScheduledTime();
        if (req.getScheduledTime() != null) {
            collection.setScheduledTime(scheduledTime);
        }

        if (req.getPlatformConfigs() != null) {
            try {
                collection.setPlatformConfigs(objectMapper.writeValueAsString(req.getPlatformConfigs()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize platformConfigs", e);
            }
        }

        if (req.getKeepMediaKeys() != null || req.getNewMedia() != null) {
            List<String> keepKeys = req.getKeepMediaKeys() != null ? req.getKeepMediaKeys() : List.of();
            List<PostMediaEntity> updatedMedia = new ArrayList<>();

            if (collection.getMediaFiles() != null) {
                updatedMedia.addAll(
                        collection.getMediaFiles().stream()
                                .filter(m -> keepKeys.contains(m.getFileKey()))
                                .collect(Collectors.toList())
                );
            }

            if (req.getNewMedia() != null) {
                for (PostMedia dto : req.getNewMedia()) {
                    PostMediaEntity entity = new PostMediaEntity();
                    entity.setFileKey(dto.getFileKey());
                    entity.setFileName(dto.getFileName());
                    entity.setMimeType(dto.getMimeType());
                    entity.setSize(dto.getSize());
                    entity.setPostCollection(collection);
                    updatedMedia.add(entity);
                }
            }

            collection.getMediaFiles().clear();
            collection.getMediaFiles().addAll(updatedMedia);
        }

        List<String> newlyAddedProviderUserIds = new ArrayList<>();
        if (req.getConnectedAccounts() != null) {
            List<ConnectedAccount> requestedAccounts = req.getConnectedAccounts();

            Set<String> requestedIds = requestedAccounts.stream()
                    .map(ConnectedAccount::getProviderUserId)
                    .collect(Collectors.toSet());
            Set<String> currentIds = collection.getPosts() != null
                    ? collection.getPosts().stream().map(PostEntity::getProviderUserId).collect(Collectors.toSet())
                    : Set.of();

            List<String> removedPostRedisKeys = new ArrayList<>();
            Iterator<PostEntity> iter = collection.getPosts().iterator();
            while (iter.hasNext()) {
                PostEntity post = iter.next();
                if (!requestedIds.contains(post.getProviderUserId())) {
                    if (post.getPostStatus() == PostStatus.SCHEDULED && post.getId() != null) {
                        removedPostRedisKeys.add(post.getId().toString());
                    }
                    iter.remove();
                }
            }
            if (!removedPostRedisKeys.isEmpty()) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.zrem(PostPoolHelper.getPostsPoolName(), removedPostRedisKeys.toArray(String[]::new));
                }
                log.info("Removed {} posts from Redis pool on account update", removedPostRedisKeys.size());
            }

            PostCollectionType postType = collection.getPostCollectionType();
            boolean collectionIsDraft = collection.getPostCollectionStatus() == PostCollectionStatus.DRAFT;
            for (ConnectedAccount account : requestedAccounts) {
                if (!currentIds.contains(account.getProviderUserId())) {
                    PostEntity newPost = new PostEntity();
                    newPost.setProvider(ProviderPlatformMapper.getProviderByPlatform(account.getPlatform()));
                    newPost.setProviderUserId(account.getProviderUserId());
                    newPost.setPostCollection(collection);
                    newPost.setPostStatus(collectionIsDraft ? PostStatus.DRAFT : PostStatus.SCHEDULED);
                    newPost.setPostType(PostTypeMapper.getPostTypeByPostCollectionType(postType));
                    newPost.setScheduledTime(scheduledTime);
                    collection.getPosts().add(newPost);
                    if (!collectionIsDraft) {
                        newlyAddedProviderUserIds.add(account.getProviderUserId());
                    }
                }
            }
        }

        if (req.getScheduledTime() != null && collection.getPosts() != null) {
            long newEpochMillis = scheduledTime.toInstant().toEpochMilli();
            try (Jedis jedis = jedisPool.getResource()) {
                for (PostEntity post : collection.getPosts()) {
                    if (post.getPostStatus() == PostStatus.SCHEDULED && post.getId() != null) {
                        post.setScheduledTime(scheduledTime);
                        jedis.zadd(PostPoolHelper.getPostsPoolName(), newEpochMillis, post.getId().toString());
                    }
                }
            }
        }

        PostCollectionEntity saved = postCollectionRepo.save(collection);

        if (!newlyAddedProviderUserIds.isEmpty()) {
            final long epochMillis = scheduledTime.toInstant().toEpochMilli();
            try (Jedis jedis = jedisPool.getResource()) {
                for (PostEntity post : saved.getPosts()) {
                    if (newlyAddedProviderUserIds.contains(post.getProviderUserId())
                            && post.getPostStatus() == PostStatus.SCHEDULED) {
                        jedis.zadd(PostPoolHelper.getPostsPoolName(), epochMillis, post.getId().toString());
                        log.info("New post added to Redis pool: postId={}, scheduleUTC={}", post.getId(), scheduledTime);
                    }
                }
            }
        }

        List<ConnectedAccount> allConnectedAccounts = accountProfileService.getAllConnectedAccounts(workspaceId);
        Map<String, ConnectedAccount> connectedAccountMap = allConnectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));
        return getPostCollectionResponse(saved, connectedAccountMap);
    }

    @Transactional(readOnly = true)
    public PostCollectionResponse getPostCollectionById(String userId, Long id) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = postCollectionRepo.findById(id)
                .orElseThrow(() -> new SocialRavenException(
                        "Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(workspaceId);
        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));
        return getPostCollectionResponse(collection, connectedAccountMap);
    }

    @Transactional(readOnly = true)
    public Page<PostCollectionResponse> getUserPostCollections(
            String userId, int page, String type, String search, List<String> providerUserIds,
            String platform, String sortDir, String dateRange) {
        String workspaceId = WorkspaceContext.getWorkspaceId();

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by("scheduledTime").ascending()
                : Sort.by("scheduledTime").descending();
        Pageable pageable = PageRequest.of(page, 12, sort);

        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(workspaceId);
        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));

        String searchPattern = (search != null && !search.trim().isEmpty())
                ? "%" + search.trim().toLowerCase() + "%"
                : null;
        boolean hasAccountFilter = !CollectionUtils.isEmpty(providerUserIds);

        String platformStr = null;
        if (platform != null && !platform.isBlank()) {
            try {
                platformStr = Provider.valueOf(platform.toUpperCase()).name();
            } catch (IllegalArgumentException ignored) {}
        }
        boolean hasPlatformFilter = platformStr != null;

        OffsetDateTime fromDate = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
        OffsetDateTime toDate   = OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
        if (dateRange != null) {
            OffsetDateTime now = OffsetDateTime.now();
            switch (dateRange.toLowerCase()) {
                case "today" -> {
                    fromDate = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
                    toDate = fromDate.plusDays(1).minusNanos(1);
                }
                case "week" -> {
                    fromDate = now.toLocalDate()
                            .with(java.time.DayOfWeek.MONDAY)
                            .atStartOfDay().atOffset(now.getOffset());
                    toDate = fromDate.plusWeeks(1).minusNanos(1);
                }
                case "month" -> {
                    fromDate = now.toLocalDate().withDayOfMonth(1).atStartOfDay().atOffset(now.getOffset());
                    toDate = fromDate.plusMonths(1).minusNanos(1);
                }
                default -> {}
            }
        }

        Page<PostCollectionEntity> collectionsPage;
        if ("scheduled".equalsIgnoreCase(type)) {
            if (hasAccountFilter && hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findScheduledCollectionsWithAccountsAndPlatform(
                        workspaceId, searchPattern, platformStr, providerUserIds, fromDate, toDate, pageable);
            } else if (hasAccountFilter) {
                collectionsPage = postCollectionRepo.findScheduledCollectionsWithAccounts(
                        workspaceId, searchPattern, providerUserIds, fromDate, toDate, pageable);
            } else if (hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findScheduledCollectionsByPlatform(
                        workspaceId, searchPattern, platformStr, fromDate, toDate, pageable);
            } else {
                collectionsPage = postCollectionRepo.findScheduledCollections(
                        workspaceId, searchPattern, fromDate, toDate, pageable);
            }
        } else if ("published".equalsIgnoreCase(type)) {
            if (hasAccountFilter && hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findPublishedCollectionsWithAccountsAndPlatform(
                        workspaceId, searchPattern, platformStr, providerUserIds, fromDate, toDate, pageable);
            } else if (hasAccountFilter) {
                collectionsPage = postCollectionRepo.findPublishedCollectionsWithAccounts(
                        workspaceId, searchPattern, providerUserIds, fromDate, toDate, pageable);
            } else if (hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findPublishedCollectionsByPlatform(
                        workspaceId, searchPattern, platformStr, fromDate, toDate, pageable);
            } else {
                collectionsPage = postCollectionRepo.findPublishedCollections(
                        workspaceId, searchPattern, fromDate, toDate, pageable);
            }
        } else if ("draft".equalsIgnoreCase(type)) {
            Sort draftSort = "asc".equalsIgnoreCase(sortDir)
                    ? Sort.by("id").ascending()
                    : Sort.by("id").descending();
            Pageable draftPageable = PageRequest.of(page, 12, draftSort);
            if (hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findDraftCollectionsByPlatform(
                        workspaceId, searchPattern, platformStr, draftPageable);
            } else {
                collectionsPage = postCollectionRepo.findDraftCollections(
                        workspaceId, searchPattern, draftPageable);
            }
        } else {
            collectionsPage = postCollectionRepo.findByWorkspaceIdOrderByScheduledTimeDesc(workspaceId, pageable);
        }
        return collectionsPage.map(c -> getPostCollectionResponse(c, connectedAccountMap));
    }

    @SuppressWarnings("unchecked")
    private PostCollectionResponse getPostCollectionResponse(
            PostCollectionEntity collection,
            Map<String, ConnectedAccount> connectedAccountMap) {

        List<PostEntity> posts = collection.getPosts();

        List<MediaResponse> mediaDtos = collection.getMediaFiles() != null
                ? collection.getMediaFiles().stream()
                        .map(m -> new MediaResponse(
                                m.getId(),
                                m.getFileName(),
                                m.getMimeType(),
                                m.getSize(),
                                storageService.generatePresignedGetUrl(m.getFileKey(), Duration.ofMinutes(10)),
                                m.getFileKey()
                        )).toList()
                : List.of();

        List<PostResponse> postDtos = posts != null
                ? posts.stream().map(p -> getPostResponse(p, connectedAccountMap)).toList()
                : List.of();

        Map<String, Object> platformConfigsMap = null;
        if (collection.getPlatformConfigs() != null && !collection.getPlatformConfigs().isBlank()) {
            try {
                platformConfigsMap = objectMapper.readValue(collection.getPlatformConfigs(), Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse platformConfigs for collection {}: {}", collection.getId(), e.getMessage());
            }
        }

        String overallStatus = collection.getPostCollectionStatus() == PostCollectionStatus.DRAFT
                ? "DRAFT"
                : deriveOverallStatus(posts);

        return new PostCollectionResponse(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getScheduledTime(),
                collection.getPostCollectionType().name(),
                overallStatus,
                postDtos,
                mediaDtos,
                platformConfigsMap
        );
    }

    private PostResponse getPostResponse(PostEntity post, Map<String, ConnectedAccount> connectedAccountMap) {
        PostCollectionEntity postCollection = post.getPostCollection();
        ConnectedAccount connectedAccount = connectedAccountMap.get(post.getProviderUserId());
        List<PostMediaEntity> mediaList = postCollection.getMediaFiles();

        List<MediaResponse> mediaDtos =
                mediaList.stream().map(m ->
                        new MediaResponse(
                                m.getId(),
                                m.getFileName(),
                                m.getMimeType(),
                                m.getSize(),
                                storageService.generatePresignedGetUrl(m.getFileKey(), Duration.ofMinutes(10)),
                                m.getFileKey()
                        )
                ).toList();

        return new PostResponse(
                post.getId(),
                postCollection.getId(),
                connectedAccount != null ? ProviderPlatformMapper.getProviderByPlatform(connectedAccount.getPlatform()) : null,
                postCollection.getTitle(),
                postCollection.getDescription(),
                post.getPostStatus().toString(),
                post.getScheduledTime(),
                mediaDtos,
                connectedAccount
        );
    }

    @Transactional(readOnly = true)
    public List<CalendarPostResponse> getCalendarPosts(
            String userId,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            List<String> providerUserIds) {
        String workspaceId = WorkspaceContext.getWorkspaceId();

        List<PostEntity> posts;
        if (CollectionUtils.isEmpty(providerUserIds)) {
            posts = postRepo.findCalendarPosts(workspaceId, startTime, endTime);
        } else {
            posts = postRepo.findCalendarPostsFiltered(workspaceId, startTime, endTime, providerUserIds);
        }

        return posts.stream()
                .map(p -> {
                    PostCollectionEntity pc = p.getPostCollection();
                    return new CalendarPostResponse(
                            p.getId(),
                            pc.getId(),
                            pc.getTitle(),
                            p.getProvider().name().toLowerCase(),
                            p.getProviderUserId(),
                            p.getPostStatus().name(),
                            pc.getPostCollectionType().name(),
                            p.getScheduledTime()
                    );
                })
                .toList();
    }

    private String deriveOverallStatus(List<PostEntity> posts) {
        if (posts == null || posts.isEmpty()) return "SCHEDULED";
        long total = posts.size();
        long scheduled = posts.stream().filter(p -> p.getPostStatus() == PostStatus.SCHEDULED).count();
        long posted = posts.stream().filter(p -> p.getPostStatus() == PostStatus.POSTED).count();
        long failed = posts.stream().filter(p -> p.getPostStatus() == PostStatus.FAILED).count();

        if (scheduled == total) return "SCHEDULED";
        if (posted == total) return "PUBLISHED";
        if (failed == total) return "FAILED";
        return "PARTIAL_SUCCESS";
    }
}
