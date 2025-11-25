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
import com.ghouse.socialraven.entity.SinglePostEntity;
import com.ghouse.socialraven.mapper.ProviderPlatformMapper;
import com.ghouse.socialraven.repo.PostMediaRepo;
import com.ghouse.socialraven.repo.SinglePostRepo;
import com.ghouse.socialraven.service.storage.StorageService;
import com.ghouse.socialraven.util.SecurityContextUtil;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;


@Service
@Slf4j
public class PostService {

    @Autowired
    private SinglePostRepo singlePostRepo;

    @Autowired
    private PostMediaRepo postMediaRepo;

    @Autowired
    private StorageService storageService;


    public SchedulePost schedulePost(SchedulePost schedulePost) {
        log.info("SchedulePost: {}", schedulePost);

        List<ConnectedAccount> connectedAccounts = schedulePost.getConnectedAccounts();
        if (CollectionUtils.isEmpty(connectedAccounts)) {
            throw new RuntimeException("Select connected accounts");
        }

        SinglePostEntity post = new SinglePostEntity();

        post.setPostStatus(PostStatus.SCHEDULED);
        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        post.setUserId(userId);
        post.setTitle(schedulePost.getTitle());
        post.setDescription(schedulePost.getDescription());
        //Single provider
        Platform platform = connectedAccounts.stream().findFirst().get().getPlatform();
        Provider provider = ProviderPlatformMapper.getProviderByPlatform(platform);
        post.setProvider(provider);
        List<String> providerIds = connectedAccounts.stream().map(x -> x.getProviderUserId()).distinct().toList();
        post.setProviderUserIds(providerIds);

        post.setScheduledTime(OffsetDateTime.now());


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

        singlePostRepo.save(post);
        return schedulePost;
    }



    public Page<PostResponse> getUserPosts(String userId, int page, PostStatus postStatus) {

        Pageable pageable;

        if (page == -1) {
            pageable = Pageable.unpaged();   // fetch all
        } else {
            pageable = PageRequest.of(page, 12, Sort.by("scheduledTime").descending());
        }

        Page<SinglePostEntity> postsPage =
                singlePostRepo.findByUserIdAndPostStatusOrderByScheduledTimeDesc(
                        userId,
                        postStatus,
                        pageable
                );



        return postsPage.map(post -> {

            List<PostMediaEntity> mediaList = post.getMediaFiles();

            List<MediaResponse> mediaDtos =
                    mediaList.stream().map(m ->
                            new MediaResponse(
                                    m.getId(),
                                    m.getFileName(),
                                    m.getMimeType(),
                                    m.getSize(),
                                    storageService.generatePresignedGetUrl(m.getFileKey()),
                                    m.getFileKey()
                            )
                    ).toList();

            return new PostResponse(
                    post.getId(),
                    post.getTitle(),
                    post.getDescription(),
                    post.getProvider().toString(),
                    post.getPostStatus().toString(),
                    post.getScheduledTime(),
                    mediaDtos,
                    List.of("ashoka", "arjun")
            );
        });
    }

    public Page<PostResponse> getUserPosts(String userId) {
        {

            Pageable pageable = Pageable.unpaged();



            Page<SinglePostEntity> postsPage =
                    singlePostRepo.findByUserIdOrderByScheduledTimeDesc(
                            userId,
                            pageable
                    );



            return postsPage.map(post -> {

                List<PostMediaEntity> mediaList = post.getMediaFiles();

                List<MediaResponse> mediaDtos =
                        mediaList.stream().map(m ->
                                new MediaResponse(
                                        m.getId(),
                                        m.getFileName(),
                                        m.getMimeType(),
                                        m.getSize(),
                                        storageService.generatePresignedGetUrl(m.getFileKey()),
                                        m.getFileKey()
                                )
                        ).toList();

                return new PostResponse(
                        post.getId(),
                        post.getTitle(),
                        post.getDescription(),
                        post.getProvider().toString(),
                        post.getPostStatus().toString(),
                        post.getScheduledTime(),
                        mediaDtos,
                        List.of("ashoka", "arjun")
                );
            });
        }
    }
}
