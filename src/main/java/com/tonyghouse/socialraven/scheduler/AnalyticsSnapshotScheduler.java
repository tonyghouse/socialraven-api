package com.tonyghouse.socialraven.scheduler;

import com.tonyghouse.socialraven.util.BatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class AnalyticsSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsSnapshotScheduler.class);
    private static final String ANALYTICS_POOL = "analytics-snapshot-pool";

    private final MessagePublisher messagePublisher;
    private final PostRedisService postRedisService;

    public AnalyticsSnapshotScheduler(MessagePublisher messagePublisher, PostRedisService postRedisService) {
        this.messagePublisher = messagePublisher;
        this.postRedisService = postRedisService;
    }

    @Scheduled(cron = "0 */5 * * * ?", zone = "UTC")
    public void dispatchDueJobs() {
        log.info("[AnalyticsScheduler] Running scheduled job");
        long nowMs = Instant.now().toEpochMilli();
        List<Long> jobIds = postRedisService.fetchIds(ANALYTICS_POOL, nowMs);
        if (jobIds.isEmpty()) {
            log.info("[AnalyticsScheduler] No analytics jobs due.");
            return;
        }
        postRedisService.removePosts(ANALYTICS_POOL, jobIds);
        List<List<Long>> batches = BatchUtil.partition(jobIds, 1000);
        for (List<Long> batch : batches) {
            messagePublisher.publishAnalyticsJobIds(batch);
        }
        log.info("[AnalyticsScheduler] Dispatched {} analytics jobs", jobIds.size());
    }
}
