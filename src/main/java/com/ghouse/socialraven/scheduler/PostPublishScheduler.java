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

    private final RabbitPublisher rabbitPublisher;
    private final PostRedisService postRedisService;

    public PostPublishScheduler(
            RabbitPublisher rabbitPublisher,
            PostRedisService postRedisService
    ) {
        this.rabbitPublisher = rabbitPublisher;
        this.postRedisService = postRedisService;
    }

    /**
     * Runs every 5 minutes
     */
    @Scheduled(cron = "0 */5 * * * ?", zone = "UTC")
    public void publishPosts() {

        log.info("[PostScheduler] Running scheduled job");

        String redisKey = getPostsPoolName();

        long next5Min = Instant.now()
                .plus(5, ChronoUnit.MINUTES)
                .toEpochMilli();

        List<Long> postIds =
                postRedisService.fetchIds(redisKey, next5Min);

        if (postIds.isEmpty()) {
            log.info("[PostScheduler] No posts to publish.");
            return;
        }

        // Remove first → avoids double publish
        postRedisService.removePosts(redisKey, postIds);

        List<List<Long>> postIdsBatches =
                BatchUtil.partition(postIds, 1000);

        for (List<Long> postIdsBatch : postIdsBatches) {
            rabbitPublisher.publishPostIds(postIdsBatch);
        }

        log.info("[PostScheduler] Picked & queued posts: {}", postIds);
    }

    private String getPostsPoolName() {
        return "posts-pool-1";
    }
}
