package com.ghouse.socialraven.scheduler;

import com.ghouse.socialraven.util.BatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class PostPublishScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(PostPublishScheduler.class);

    private final MessagePublisher messagePublisher;
    private final PostRedisService postRedisService;

    public PostPublishScheduler(
            MessagePublisher messagePublisher,
            PostRedisService postRedisService
    ) {
        this.messagePublisher = messagePublisher;
        this.postRedisService = postRedisService;
    }

    /**
     * Runs every 3 minutes
     */
    @Scheduled(cron = "0 */3 * * * ?", zone = "UTC")
    public void publishPosts() {

        log.info("[PostScheduler] Running scheduled job");

        String redisKey = getPostsPoolName();

        long next3Min = Instant.now()
                .plus(3, ChronoUnit.MINUTES)
                .toEpochMilli();

        List<Long> postIds =
                postRedisService.fetchIds(redisKey, next3Min);

        if (postIds.isEmpty()) {
            log.info("[PostScheduler] No posts to publish.");
            return;
        }

        // Remove first → avoids double publish
        postRedisService.removePosts(redisKey, postIds);

        List<List<Long>> postIdsBatches =
                BatchUtil.partition(postIds, 1000);

        for (List<Long> postIdsBatch : postIdsBatches) {
            messagePublisher.publishPostIds(postIdsBatch);
        }

        log.info("[PostScheduler] Picked & queued posts: {}", postIds);
    }

    private String getPostsPoolName() {
        return "posts-pool-1";
    }
}
