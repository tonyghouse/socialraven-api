package com.tonyghouse.socialraven.scheduler;

import com.tonyghouse.socialraven.service.analytics.AnalyticsBackboneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsAccountSyncScheduler {

    private final AnalyticsBackboneService analyticsBackboneService;

    @Scheduled(cron = "0 15 0 * * *", zone = "UTC")
    public void scheduleDailyAccountSyncs() {
        analyticsBackboneService.scheduleDailyAccountSyncs();
        analyticsBackboneService.scheduleNightlyProviderReconciles();
        log.info("[AnalyticsAccountSyncScheduler] Scheduled daily analytics account sync jobs");
    }
}
