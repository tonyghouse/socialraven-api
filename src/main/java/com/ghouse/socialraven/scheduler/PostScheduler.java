package com.ghouse.socialraven.scheduler;

import com.ghouse.socialraven.service.post.PostPublisherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PostScheduler {

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private PostPublisherService postPublisherService;

    /**
     * Runs every 5 mins in UTC
     */
    @Scheduled(cron = "0 */1 * * * *", zone = "UTC")
    public void schedulePosts() {
        log.info("Running Schedule Posts");
        String redisKey = "posts-pool-1";

        long now = Instant.now().toEpochMilli();
        long next5Min = Instant.now().plus(5, ChronoUnit.MINUTES).toEpochMilli();

        try (Jedis jedis = jedisPool.getResource()) {

            // Pick overdue + next 5 min = get everything less than now + 5min
            List<String> postIdsFromRedis = jedis.zrangeByScore(redisKey, "-inf", String.valueOf(next5Min));

            if (postIdsFromRedis == null || postIdsFromRedis.isEmpty()) {
                log.info("[PostScheduler] No posts to publish this cycle.");
                return;
            }

            List<Long> postIds = postIdsFromRedis.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            // Prevent requeue → remove from pool after picking
            jedis.zrem(redisKey, postIdsFromRedis.toArray(new String[0]));

            postPublisherService.publishPosts(postIds);

            log.info("[PostScheduler] Picked & queued overdue + upcoming posts: {}", postIds);
        }
    }
}
