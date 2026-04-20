package com.tonyghouse.socialraven.service.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.tonyghouse.socialraven.dto.reporting.ClientReportPlatformPerformanceResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportSummaryResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportTopPostResponse;
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
                "WORKSPACE",
                "Workspace",
                null,
                null,
                "Performance held steady with stronger engagement on LinkedIn and Instagram.",
                List.of(
                        "Delivered 18,000 impressions and 495 engagements across the selected report window.",
                        "Instagram led overall engagement while LinkedIn supported reach."
                ),
                OffsetDateTime.parse("2026-04-04T08:00:00Z"),
                OffsetDateTime.parse("2026-04-18T08:00:00Z"),
                new ClientReportSummaryResponse(
                        "Last 30 days",
                        OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-30T23:59:59Z"),
                        18000,
                        401,
                        4.12,
                        55,
                        0,
                        19
                ),
                List.of(
                        new ClientReportPlatformPerformanceResponse("INSTAGRAM", "Instagram", 11, 280, 11000, 25.4, 57.9, 69.8, 61.1),
                        new ClientReportPlatformPerformanceResponse("LINKEDIN", "LinkedIn", 8, 121, 7000, 15.1, 42.1, 30.2, 38.9)
                ),
                List.of(
                        new ClientReportTopPostResponse(
                                91L,
                                "INSTAGRAM",
                                "Instagram",
                                "Orbit Foods",
                                null,
                                null,
                                "A concise campaign clip that outperformed the rest of the month.",
                                "VIDEO",
                                "SHORT_VIDEO",
                                OffsetDateTime.parse("2026-04-02T09:00:00Z"),
                                6100L,
                                4800L,
                                210L,
                                31L,
                                14L,
                                0L,
                                0L,
                                0L,
                                0L,
                                255L,
                                4.18
                        )
                ),
                List.of(),
                null,
                null
        );

        byte[] pdf = service.render(report);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
