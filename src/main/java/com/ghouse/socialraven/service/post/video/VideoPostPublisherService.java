package com.ghouse.socialraven.service.post.video;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.repo.PostRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VideoPostPublisherService {

    @Autowired
    private PostRepo postRepo;

    public void publishPost(PostEntity post) {

        post.setPostStatus(PostStatus.POSTED);
        postRepo.save(post);
    }
}
