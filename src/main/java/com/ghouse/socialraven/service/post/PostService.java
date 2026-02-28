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
import com.ghouse.socialraven.entity.PostCollectionEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.helper.PostPoolHelper;
import com.ghouse.socialraven.mapper.PostTypeMapper;
import com.ghouse.socialraven.mapper.ProviderPlatformMapper;
import com.ghouse.socialraven.repo.PostCollectionRepo;
import com.ghouse.socialraven.repo.PostMediaRepo;
import com.ghouse.socialraven.repo.PostRepo;
import com.ghouse.socialraven.service.account_profile.AccountProfileService;
import com.ghouse.socialraven.service.storage.StorageService;
import com.ghouse.socialraven.util.SecurityContextUtil;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
        List<ConnectedAccount> connectedAccounts = postCollectionReq.getConnectedAccounts();
        if (CollectionUtils.isEmpty(connectedAccounts)) {
            throw new RuntimeException("Select connected accounts for post collection");
        }

        PostCollectionEntity postCollection = new PostCollectionEntity();


        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        postCollection.setPostCollectionStatus(PostCollectionStatus.SCHEDULED);
        PostCollectionType postType = postCollectionReq.getPostType();
        postCollection.setPostCollectionType(postType);
        postCollection.setUserId(userId);
        postCollection.setTitle(postCollectionReq.getTitle());
        postCollection.setDescription(postCollectionReq.getDescription());
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
        for (ConnectedAccount connectedAccount : connectedAccounts) {
            PostEntity post = new PostEntity();
            post.setProvider(ProviderPlatformMapper.getProviderByPlatform(connectedAccount.getPlatform()));
            post.setProviderUserId(connectedAccount.getProviderUserId());
            post.setPostCollection(postCollection);
            post.setPostStatus(PostStatus.SCHEDULED);
            post.setPostType(PostTypeMapper.getPostTypeByPostCollectionType(postType));
            post.setScheduledTime(scheduledTime);
            postEntities.add(post);
        }
        postCollection.setPosts(postEntities);

        PostCollectionEntity savedPost = postCollectionRepo.save(postCollection);

        List<PostEntity> posts = savedPost.getPosts();
        for (PostEntity post : posts) {
            OffsetDateTime scheduleTime = post.getScheduledTime();
            long epochUtcMillis = scheduleTime.toInstant().toEpochMilli();

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.zadd(PostPoolHelper.getPostsPoolName(), epochUtcMillis, post.getId().toString());
                log.info("Scheduled Post Added to Redis pool: postId={}, scheduleUTC={}", post.getId(), scheduleTime);
            }
        }

        return postCollectionReq;
    }



    public Page<PostResponse> getUserPosts(String userId, int page, PostStatus postStatus) {
        Pageable pageable;
        if (page == -1) {
            pageable = Pageable.unpaged();   // fetch all
        } else {
            pageable = PageRequest.of(page, 12, Sort.by("scheduledTime").descending());
        }


        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(userId);

        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));


        Page<PostEntity> postsPage = postRepo.findByPostCollectionUserIdAndPostStatus(userId, postStatus, pageable);
        return postsPage.map(p -> getPostResponse(p, connectedAccountMap));
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
                connectedAccount!=null? ProviderPlatformMapper.getProviderByPlatform(connectedAccount.getPlatform()) : null,
                postCollection.getTitle(),
                postCollection.getDescription(),
                post.getPostStatus().toString(),
                post.getScheduledTime(),
                mediaDtos,
                connectedAccount
        );
    }

    public Page<PostResponse> getUserPosts(String userId) {
        Pageable pageable = Pageable.unpaged();
        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(userId);

        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));

        Page<PostEntity> postsPage = postRepo.findByPostCollectionUserId(userId, pageable);
        return postsPage.map(p -> getPostResponse(p, connectedAccountMap));
    }

    public PostResponse getPostById(String userId, Long postId) {
        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(userId);

        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));


        PostEntity post = postRepo.findById(postId).orElse(null);
        if(post == null){
            throw new RuntimeException("Post not found");
        }

        return getPostResponse(post, connectedAccountMap);
    }

    public void deletePostById(String userId, Long postId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zrem(PostPoolHelper.getPostsPoolName(), postId.toString());

        }

        postRepo.deleteById(postId);
    }

    @Transactional(readOnly = true)
    public PostCollectionResponse getPostCollectionById(String userId, Long id) {
        PostCollectionEntity collection = postCollectionRepo.findById(id)
                .orElseThrow(() -> new com.ghouse.socialraven.exception.SocialRavenException(
                        "Post collection not found", org.springframework.http.HttpStatus.NOT_FOUND));
        if (!collection.getUserId().equals(userId)) {
            throw new com.ghouse.socialraven.exception.SocialRavenException(
                    "Access denied", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(userId);
        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));
        return getPostCollectionResponse(collection, connectedAccountMap);
    }

    @Transactional(readOnly = true)
    public Page<PostCollectionResponse> getUserPostCollections(
            String userId, int page, String type, String search, List<String> providerUserIds) {
        Pageable pageable = PageRequest.of(page, 12, Sort.by("scheduledTime").descending());

        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(userId);
        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));

        // Build LIKE pattern: lowercase %term%, or null when no search
        String searchPattern = (search != null && !search.trim().isEmpty())
                ? "%" + search.trim().toLowerCase() + "%"
                : null;
        boolean hasSearch = searchPattern != null;
        boolean hasAccountFilter = !CollectionUtils.isEmpty(providerUserIds);

        Page<PostCollectionEntity> collectionsPage;
        if ("scheduled".equalsIgnoreCase(type)) {
            if (hasSearch || hasAccountFilter) {
                if (hasAccountFilter) {
                    collectionsPage = postCollectionRepo.searchScheduledCollectionsWithAccounts(
                            userId, searchPattern, providerUserIds, pageable);
                } else {
                    collectionsPage = postCollectionRepo.searchScheduledCollections(
                            userId, searchPattern, pageable);
                }
            } else {
                collectionsPage = postCollectionRepo.findScheduledCollectionsByUserId(userId, pageable);
            }
        } else if ("published".equalsIgnoreCase(type)) {
            if (hasSearch || hasAccountFilter) {
                if (hasAccountFilter) {
                    collectionsPage = postCollectionRepo.searchPublishedCollectionsWithAccounts(
                            userId, searchPattern, providerUserIds, pageable);
                } else {
                    collectionsPage = postCollectionRepo.searchPublishedCollections(
                            userId, searchPattern, pageable);
                }
            } else {
                collectionsPage = postCollectionRepo.findPublishedCollectionsByUserId(userId, pageable);
            }
        } else {
            collectionsPage = postCollectionRepo.findByUserIdOrderByScheduledTimeDesc(userId, pageable);
        }
        return collectionsPage.map(c -> getPostCollectionResponse(c, connectedAccountMap));
    }

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

        return new PostCollectionResponse(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getScheduledTime(),
                collection.getPostCollectionType().name(),
                deriveOverallStatus(posts),
                postDtos,
                mediaDtos
        );
    }

    @Transactional(readOnly = true)
    public List<CalendarPostResponse> getCalendarPosts(
            String userId,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            List<String> providerUserIds) {

        List<PostEntity> posts;
        if (CollectionUtils.isEmpty(providerUserIds)) {
            posts = postRepo.findCalendarPosts(userId, startTime, endTime);
        } else {
            posts = postRepo.findCalendarPostsFiltered(userId, startTime, endTime, providerUserIds);
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
