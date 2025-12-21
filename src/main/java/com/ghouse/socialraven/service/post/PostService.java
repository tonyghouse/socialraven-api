package com.ghouse.socialraven.service.post;

import com.ghouse.socialraven.constant.Platform;
import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.dto.ConnectedAccount;
import com.ghouse.socialraven.dto.MediaResponse;
import com.ghouse.socialraven.dto.PostMedia;
import com.ghouse.socialraven.dto.PostResponse;
import com.ghouse.socialraven.dto.SchedulePost;
import com.ghouse.socialraven.entity.PostMediaEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.helper.PostPoolHelper;
import com.ghouse.socialraven.mapper.ProviderPlatformMapper;
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
import org.springframework.util.CollectionUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;


@Service
@Slf4j
public class PostService {

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


    public SchedulePost schedulePost(SchedulePost schedulePost) {
        List<ConnectedAccount> connectedAccounts = schedulePost.getConnectedAccounts();
        if (CollectionUtils.isEmpty(connectedAccounts)) {
            throw new RuntimeException("Select connected accounts");
        }

        PostEntity post = new PostEntity();

        post.setPostStatus(PostStatus.SCHEDULED);
        post.setPostType(schedulePost.getPostType());
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        post.setUserId(userId);
        post.setTitle(schedulePost.getTitle());
        post.setDescription(schedulePost.getDescription());
        //Single provider
        Platform platform = connectedAccounts.stream().findFirst().get().getPlatform();
        List<String> providerIds = connectedAccounts.stream().map(x -> x.getProviderUserId()).distinct().toList();
        post.setProviderUserIds(providerIds);

        post.setScheduledTime(schedulePost.getScheduledTime());


        List<PostMedia> media = schedulePost.getMedia() != null ? schedulePost.getMedia() : Collections.emptyList();
        List<PostMediaEntity> postMediaEntityList = new ArrayList<>();
        for (var postMediaDto : media) {
            PostMediaEntity postMediaEntity = new PostMediaEntity();
            postMediaEntity.setFileUrl(postMediaDto.getFileUrl());
            postMediaEntity.setFileKey(postMediaDto.getFileKey());
            postMediaEntity.setSize(postMediaDto.getSize());
            postMediaEntity.setFileName(postMediaDto.getFileName());
            postMediaEntity.setMimeType(postMediaDto.getMimeType());
            postMediaEntity.setPost(post);
            postMediaEntityList.add(postMediaEntity);
        }

        post.setMediaFiles(postMediaEntityList);


        PostEntity savedPost = postRepo.save(post);
        Long postId = savedPost.getId();

        // ---------------- REDIS SCHEDULING ------------------
        OffsetDateTime scheduleTime = savedPost.getScheduledTime();
        long epochUtcMillis = scheduleTime.toInstant().toEpochMilli();

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd(PostPoolHelper.getPostsPoolName(), epochUtcMillis, postId.toString());
            log.info("Scheduled Post Added to Redis pool: postId={}, scheduleUTC={}", postId, scheduleTime);
        }

        return schedulePost;
    }



    public Page<PostResponse> getUserPosts(String userId, int page, PostStatus postStatus) {
        Pageable pageable;
        if (page == -1) {
            pageable = Pageable.unpaged();   // fetch all
        } else {
            pageable = PageRequest.of(page, 3, Sort.by("scheduledTime").descending());
        }


        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(userId);

        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));


        Page<PostEntity> postsPage = postRepo
                .findByUserIdAndPostStatusOrderByScheduledTimeDesc(userId, postStatus, pageable);
        return postsPage.map(p -> getPostResponse(p, connectedAccountMap));
    }

    private PostResponse getPostResponse(PostEntity post, Map<String, ConnectedAccount> connectedAccountMap) {

        List<String> providerUserIds = post.getProviderUserIds();
        List<ConnectedAccount> connectedAccounts = new ArrayList<>();
        for (String providerUserId : providerUserIds) {
            ConnectedAccount connectedAccount = connectedAccountMap.get(providerUserId);
            if (connectedAccount != null) {
                connectedAccounts.add(connectedAccount);
            }
        }
        List<PostMediaEntity> mediaList = post.getMediaFiles();


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
                post.getTitle(),
                post.getDescription(),
                post.getPostStatus().toString(),
                post.getScheduledTime(),
                mediaDtos,
                connectedAccounts
        );
    }

    public Page<PostResponse> getUserPosts(String userId) {
        Pageable pageable = Pageable.unpaged();
        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(userId);

        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));

        Page<PostEntity> postsPage = postRepo.findByUserIdOrderByScheduledTimeDesc(userId, pageable);

        return postsPage.map(post -> {
            return getPostResponse(post, connectedAccountMap);
        });
    }
}
