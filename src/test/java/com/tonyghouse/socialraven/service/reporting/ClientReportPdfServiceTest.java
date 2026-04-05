package com.tonyghouse.socialraven.service.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewResponse;
import com.tonyghouse.socialraven.dto.analytics.PlatformStatsResponse;
import com.tonyghouse.socialraven.dto.analytics.TimelinePointResponse;
import com.tonyghouse.socialraven.dto.analytics.TopPostResponse;
import com.tonyghouse.socialraven.dto.reporting.PublicClientReportResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientReportPdfServiceTest {

    @Test
    void renderProducesPdfDocument() {
        ClientReportPdfService service = new ClientReportPdfService();
        PublicClientReportResponse report = new PublicClientReportResponse(
                "Orbit Foods monthly report",
                "Orbit Foods",
                "Northshore Agency",
                "Orbit Foods",
                "Northshore Agency",
                null,
                "EXECUTIVE_SUMMARY",
                30,
                "Last 30 days",
                "Performance held steady with stronger engagement on LinkedIn and Instagram.",
                List.of(
                        "Delivered 18,000 impressions and 495 engagements across the selected report window.",
                        "Instagram led overall engagement while LinkedIn supported reach."
                ),
                OffsetDateTime.parse("2026-04-04T08:00:00Z"),
                OffsetDateTime.parse("2026-04-18T08:00:00Z"),
                new AnalyticsOverviewResponse(18000, 12000, 401, 66, 28, 0, 124, 19, 4.12),
                List.of(
                        new PlatformStatsResponse("INSTAGRAM", 11000, 8000, 280, 44, 19, 0, 0, 91, 11, 4.17),
                        new PlatformStatsResponse("LINKEDIN", 7000, 4000, 121, 22, 9, 0, 0, 33, 8, 3.44)
                ),
                List.of(
                        new TopPostResponse(
                                91L,
                                "INSTAGRAM",
                                "insta_91",
                                "A concise campaign clip that outperformed the rest of the month.",
                                OffsetDateTime.parse("2026-04-02T09:00:00Z"),
                                "T30D",
                                6100,
                                4800,
                                210,
                                31,
                                14,
                                4.18
                        )
                ),
                List.of(new TimelinePointResponse("2026-04-02", "INSTAGRAM", 82L))
        );

        byte[] pdf = service.render(report);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
