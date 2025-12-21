package com.ghouse.socialraven.consumer;

import com.ghouse.socialraven.service.post.PostPublisherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PostScheduleConsumer {

    private final PostPublisherService postPublisherService;

    public PostScheduleConsumer(PostPublisherService postPublisherService) {
        this.postPublisherService = postPublisherService;
    }

    /**
     * Consumes messages from RabbitMQ queue: post-publish-queue
     */
    @RabbitListener(
            queues = "post-publish-queue",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void schedulePosts(String postId) {
        try {
            Long id = Long.valueOf(postId);
            log.info("Received post publish event for postId={}", id);

            postPublisherService.publishPost(id);

        } catch (Exception e) {
            log.error("Failed to process post publish message: {}", postId, e);
            //TODO throw e; // important → lets RabbitMQ retry / DLQ if configured
        }
    }
}
