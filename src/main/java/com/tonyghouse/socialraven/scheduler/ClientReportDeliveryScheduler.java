package com.tonyghouse.socialraven.scheduler;

import com.tonyghouse.socialraven.service.reporting.ClientReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ClientReportDeliveryScheduler {

    @Autowired
    private ClientReportService clientReportService;

    @Scheduled(fixedDelayString = "${socialraven.client-report.scheduler.fixed-delay-ms:900000}")
    public void deliverDueClientReports() {
        clientReportService.processDueSchedules();
    }
}
