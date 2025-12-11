package com.ghouse.socialraven.scheduler;

import com.ghouse.socialraven.service.post.PostPublisherService;
import com.ghouse.socialraven.service.provider.OAuthInfoRefreshService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class OAuthRefreshScheduler {

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private OAuthInfoRefreshService oAuthInfoRefreshService;

    /**
     * Runs every 5 mins in UTC
     */
    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void refreshOAuthTokens() {
        log.info("Running Refresh OAuth tokens");
        String redisKey = "oauth-expiry-pool";

        long now = Instant.now().toEpochMilli();
        long next5Min = Instant.now().plus(5, ChronoUnit.MINUTES).toEpochMilli();

        try (Jedis jedis = jedisPool.getResource()) {

            // Pick overdue + next 5 min = get everything less than now + 5min
            List<String> ouathInfoFromRedis = jedis.zrangeByScore(redisKey, "-inf", String.valueOf(next5Min));

            if (ouathInfoFromRedis == null || ouathInfoFromRedis.isEmpty()) {
                log.info("[OAuthRefreshScheduler] No OAuth tokens to refresh this cycle.");
                return;
            }

            List<Long> oauthInfoIds = ouathInfoFromRedis.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            // Prevent requeue → remove from pool after picking
            jedis.zrem(redisKey, ouathInfoFromRedis.toArray(new String[0]));

            oAuthInfoRefreshService.refreshOAuthInfos(oauthInfoIds);

            log.info("[OAuthRefreshScheduler] Picked & queued overdue + upcoming refresh token: {}", oauthInfoIds);
        }
    }
}
