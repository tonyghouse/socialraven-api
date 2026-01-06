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
public class OAuthRefreshScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(OAuthRefreshScheduler.class);

    private final RabbitPublisher rabbitPublisher;
    private final PostRedisService postRedisService;

    public OAuthRefreshScheduler(
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

        log.info("[OAuthRefreshScheduler] Running scheduled job");

        String redisKey = "oauth-expiry-pool";

        long next5Min = Instant.now()
                .plus(5, ChronoUnit.MINUTES)
                .toEpochMilli();

        List<Long> oauthInfoIds =
                postRedisService.fetchIds(redisKey, next5Min);

        if (oauthInfoIds.isEmpty()) {
            log.info("[OAuthRefreshScheduler] No OAuthInfos to refresh.");
            return;
        }

        List<List<Long>> oauthInfoBatches =
                BatchUtil.partition(oauthInfoIds, 1000);

        for (List<Long> oauthInfoIdsBatch : oauthInfoBatches) {
            rabbitPublisher.refreshOAuthInfoIds(oauthInfoIdsBatch);
        }

        log.info(
                "[OAuthRefreshScheduler] Picked & queued overdue + upcoming refresh token: {}",
                oauthInfoIds
        );
    }
}
