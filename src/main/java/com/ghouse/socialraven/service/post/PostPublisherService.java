package com.ghouse.socialraven.service.post;

import com.ghouse.socialraven.constant.PostStatus;
import com.ghouse.socialraven.constant.PostType;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.repo.PostRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PostPublisherService {

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private ImagePostPublisherService imagePostPublisherService;

    @Autowired
    private VideoPostPublisherService videoPostPublisherService;

    @Autowired
    private TextPostPublisherService textPostPublisherService;

    public void publishPosts(List<Long> postIds) {
        for (Long postId : postIds) {
            log.info("Publishing post: {}", postId);
            PostEntity post = postRepo.findByIdWithMedia(postId);
            if (post !=null) {
                publishPost(post);
            }
        }

    }

    private void publishPost(PostEntity post) {
        try {
            if (PostType.IMAGE.equals(post.getPostType())) {
                imagePostPublisherService.publishPost(post);
            } else if (PostType.VIDEO.equals(post.getPostType())) {
                videoPostPublisherService.publishPost(post);
            } else {
                textPostPublisherService.publishPost(post);
            }
        } catch (Exception exp) {
            log.error("Failed to post postID:{}, postTitle: {}: ", post.getId(), post.getTitle(), exp);
            post.setPostStatus(PostStatus.FAILED);
            postRepo.save(post);
        }

    }
}
