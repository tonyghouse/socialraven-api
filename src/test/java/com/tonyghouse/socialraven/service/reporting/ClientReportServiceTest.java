package com.tonyghouse.socialraven.service.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.ClientReportScope;
import com.tonyghouse.socialraven.constant.ClientReportTemplate;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsBreakdownResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsBreakdownRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsComparableBenchmarkResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsDrilldownContributionResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsDrilldownSummaryResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsEndOfPeriodForecastResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsForecastBestSlotResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsForecastPanelResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsForecastPredictionResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsForecastRangeResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewMetricResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPercentileRankResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostRankingsResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTrendExplorerPointResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTrendExplorerResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsWorkspaceOverviewResponse;
import com.tonyghouse.socialraven.dto.reporting.CreateClientReportLinkRequest;
import com.tonyghouse.socialraven.dto.reporting.CreateClientReportScheduleRequest;
import com.tonyghouse.socialraven.dto.reporting.PublicClientReportResponse;
import com.tonyghouse.socialraven.entity.CompanyEntity;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientReportLinkEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientReportScheduleEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.CompanyRepo;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.WorkspaceClientReportLinkRepo;
import com.tonyghouse.socialraven.repo.WorkspaceClientReportScheduleRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.EmailService;
import com.tonyghouse.socialraven.service.analytics.AnalyticsWorkspaceService;
import com.tonyghouse.socialraven.service.storage.StorageService;
import com.tonyghouse.socialraven.service.workspace.WorkspaceCapabilityService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ClientReportServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void createLinkEmailsRecipientAndDefaultsBrandLabels() {
        WorkspaceClientReportLinkRepo linkRepo = mock(WorkspaceClientReportLinkRepo.class);
        WorkspaceClientReportScheduleRepo scheduleRepo = mock(WorkspaceClientReportScheduleRepo.class);
        WorkspaceCapabilityService capabilityService = mock(WorkspaceCapabilityService.class);
        ClientReportTokenService tokenService = createTokenService();
        AnalyticsWorkspaceService analyticsWorkspaceService = mock(AnalyticsWorkspaceService.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        StorageService storageService = mock(StorageService.class);
        EmailService emailService = mock(EmailService.class);

        ClientReportService service = createService(
                linkRepo,
                scheduleRepo,
                capabilityService,
                tokenService,
                analyticsWorkspaceService,
                workspaceRepo,
                companyRepo,
                postCollectionRepo,
                storageService,
                emailService
        );

        WorkspaceContext.set("workspace_1", WorkspaceRole.ADMIN);

        WorkspaceEntity workspace = workspace("workspace_1", "company_1", "Orbit Foods");
        CompanyEntity company = company("company_1", "Northshore Agency");

        when(capabilityService.hasCapability(
                "workspace_1",
                "user_1",
                WorkspaceRole.ADMIN,
                WorkspaceCapability.EXPORT_CLIENT_REPORTS
        )).thenReturn(true);
        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1")).thenReturn(Optional.of(workspace));
        when(companyRepo.findById("company_1")).thenReturn(Optional.of(company));
        when(storageService.generatePresignedGetUrl(eq("logos/agency.png"), any(Duration.class)))
                .thenReturn("https://cdn.socialraven.test/agency.png");
        mockWorkspaceAnalytics(analyticsWorkspaceService, null);
        when(linkRepo.save(any(WorkspaceClientReportLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateClientReportLinkRequest request = new CreateClientReportLinkRequest();
        request.setRecipientName("Ava Client");
        request.setRecipientEmail("ava@client.com");
        request.setTemplateType("EXECUTIVE_SUMMARY");
        request.setReportDays(30);

        var response = service.createLink("user_1", request);

        ArgumentCaptor<WorkspaceClientReportLinkEntity> linkCaptor =
                ArgumentCaptor.forClass(WorkspaceClientReportLinkEntity.class);
        verify(linkRepo).save(linkCaptor.capture());
        WorkspaceClientReportLinkEntity saved = linkCaptor.getValue();

        assertThat(saved.getClientLabel()).isEqualTo("Orbit Foods");
        assertThat(saved.getAgencyLabel()).isEqualTo("Northshore Agency");
        assertThat(saved.getTemplateType()).isEqualTo(ClientReportTemplate.EXECUTIVE_SUMMARY);
        assertThat(saved.getReportScope()).isEqualTo(ClientReportScope.WORKSPACE);
        assertThat(saved.getRecipientEmail()).isEqualTo("ava@client.com");
        assertThat(response.isActive()).isTrue();
        assertThat(response.getAgencyLabel()).isEqualTo("Northshore Agency");
        assertThat(response.getReportScope()).isEqualTo("WORKSPACE");

        verify(emailService).sendClientReportEmail(
                eq("ava@client.com"),
                eq("Ava Client"),
                eq(saved.getReportTitle()),
                eq("Orbit Foods"),
                eq("Northshore Agency"),
                eq("Last 30 days"),
                any(String.class),
                any(List.class),
                any(String.class)
        );
    }

    @Test
    void createScheduleStoresCampaignScopeAndComputesFutureNextSendAt() {
        WorkspaceClientReportLinkRepo linkRepo = mock(WorkspaceClientReportLinkRepo.class);
        WorkspaceClientReportScheduleRepo scheduleRepo = mock(WorkspaceClientReportScheduleRepo.class);
        WorkspaceCapabilityService capabilityService = mock(WorkspaceCapabilityService.class);
        AnalyticsWorkspaceService analyticsWorkspaceService = mock(AnalyticsWorkspaceService.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);

        ClientReportService service = createService(
                linkRepo,
                scheduleRepo,
                capabilityService,
                createTokenService(),
                analyticsWorkspaceService,
                workspaceRepo,
                companyRepo,
                postCollectionRepo,
                mock(StorageService.class),
                mock(EmailService.class)
        );

        WorkspaceContext.set("workspace_1", WorkspaceRole.ADMIN);

        WorkspaceEntity workspace = workspace("workspace_1", "company_1", "Orbit Foods");
        CompanyEntity company = company("company_1", "Northshore Agency");
        PostCollectionEntity campaign = campaign("workspace_1", 91L, "April launch push");

        when(capabilityService.hasCapability(
                "workspace_1",
                "user_1",
                WorkspaceRole.ADMIN,
                WorkspaceCapability.EXPORT_CLIENT_REPORTS
        )).thenReturn(true);
        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1")).thenReturn(Optional.of(workspace));
        when(companyRepo.findById("company_1")).thenReturn(Optional.of(company));
        when(postCollectionRepo.findByIdAndWorkspaceId(91L, "workspace_1")).thenReturn(Optional.of(campaign));
        when(scheduleRepo.save(any(WorkspaceClientReportScheduleEntity.class))).thenAnswer(invocation -> {
            WorkspaceClientReportScheduleEntity entity = invocation.getArgument(0);
            entity.setId(41L);
            return entity;
        });

        CreateClientReportScheduleRequest request = new CreateClientReportScheduleRequest();
        request.setRecipientEmail("ops@client.com");
        request.setCadence("WEEKLY");
        request.setDayOfWeek(5);
        request.setHourOfDayUtc(9);
        request.setReportDays(30);
        request.setTemplateType("ENGAGEMENT_SPOTLIGHT");
        request.setReportScope("CAMPAIGN");
        request.setCampaignId(91L);

        var response = service.createSchedule("user_1", request);

        assertThat(response.getId()).isEqualTo(41L);
        assertThat(response.getCadence()).isEqualTo("WEEKLY");
        assertThat(response.getTemplateType()).isEqualTo("ENGAGEMENT_SPOTLIGHT");
        assertThat(response.getReportScope()).isEqualTo("CAMPAIGN");
        assertThat(response.getCampaignId()).isEqualTo(91L);
        assertThat(response.getCampaignLabel()).contains("April launch push");
        assertThat(response.getNextSendAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void createLinkRejectsCampaignScopeWithoutCampaignId() {
        WorkspaceClientReportLinkRepo linkRepo = mock(WorkspaceClientReportLinkRepo.class);
        WorkspaceClientReportScheduleRepo scheduleRepo = mock(WorkspaceClientReportScheduleRepo.class);
        WorkspaceCapabilityService capabilityService = mock(WorkspaceCapabilityService.class);
        AnalyticsWorkspaceService analyticsWorkspaceService = mock(AnalyticsWorkspaceService.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);

        ClientReportService service = createService(
                linkRepo,
                scheduleRepo,
                capabilityService,
                createTokenService(),
                analyticsWorkspaceService,
                workspaceRepo,
                companyRepo,
                postCollectionRepo,
                mock(StorageService.class),
                mock(EmailService.class)
        );

        WorkspaceContext.set("workspace_1", WorkspaceRole.ADMIN);

        WorkspaceEntity workspace = workspace("workspace_1", "company_1", "Orbit Foods");
        CompanyEntity company = company("company_1", "Northshore Agency");

        when(capabilityService.hasCapability(
                "workspace_1",
                "user_1",
                WorkspaceRole.ADMIN,
                WorkspaceCapability.EXPORT_CLIENT_REPORTS
        )).thenReturn(true);
        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1")).thenReturn(Optional.of(workspace));
        when(companyRepo.findById("company_1")).thenReturn(Optional.of(company));

        CreateClientReportLinkRequest request = new CreateClientReportLinkRequest();
        request.setReportScope("CAMPAIGN");

        assertThatThrownBy(() -> service.createLink("user_1", request))
                .isInstanceOf(SocialRavenException.class)
                .hasMessage("campaignId is required when reportScope is CAMPAIGN");
    }

    @Test
    void getPublicReportBuildsShareSafeSnapshotFromWorkspaceAnalytics() {
        WorkspaceClientReportLinkRepo linkRepo = mock(WorkspaceClientReportLinkRepo.class);
        WorkspaceClientReportScheduleRepo scheduleRepo = mock(WorkspaceClientReportScheduleRepo.class);
        WorkspaceCapabilityService capabilityService = mock(WorkspaceCapabilityService.class);
        ClientReportTokenService tokenService = createTokenService();
        AnalyticsWorkspaceService analyticsWorkspaceService = mock(AnalyticsWorkspaceService.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        StorageService storageService = mock(StorageService.class);

        ClientReportService service = createService(
                linkRepo,
                scheduleRepo,
                capabilityService,
                tokenService,
                analyticsWorkspaceService,
                workspaceRepo,
                companyRepo,
                postCollectionRepo,
                storageService,
                mock(EmailService.class)
        );

        WorkspaceEntity workspace = workspace("workspace_1", "company_1", "Orbit Foods");
        CompanyEntity company = company("company_1", "Northshore Agency");
        WorkspaceClientReportLinkEntity link = new WorkspaceClientReportLinkEntity();
        link.setId("link_1");
        link.setWorkspaceId("workspace_1");
        link.setCreatedByUserId("user_1");
        link.setReportTitle("Orbit Foods monthly report");
        link.setClientLabel("Orbit Foods");
        link.setAgencyLabel("Northshore Agency");
        link.setReportScope(ClientReportScope.WORKSPACE);
        link.setTemplateType(ClientReportTemplate.GROWTH_SNAPSHOT);
        link.setReportDays(30);
        link.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
        link.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        when(linkRepo.findById("link_1")).thenReturn(Optional.of(link));
        when(linkRepo.save(any(WorkspaceClientReportLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1")).thenReturn(Optional.of(workspace));
        when(companyRepo.findById("company_1")).thenReturn(Optional.of(company));
        when(storageService.generatePresignedGetUrl(eq("logos/agency.png"), any(Duration.class)))
                .thenReturn("https://cdn.socialraven.test/agency.png");
        mockWorkspaceAnalytics(analyticsWorkspaceService, null);

        String token = tokenService.generateToken("link_1", link.getExpiresAt());
        PublicClientReportResponse response = service.getPublicReport(token);

        assertThat(response.getAgencyLabel()).isEqualTo("Northshore Agency");
        assertThat(response.getLogoUrl()).isEqualTo("https://cdn.socialraven.test/agency.png");
        assertThat(response.getHighlights()).isNotEmpty();
        assertThat(response.getSummary().getEngagements()).isEqualTo(401L);
        assertThat(response.getPlatformPerformance()).isNotEmpty();
        assertThat(response.getTopPosts()).hasSize(1);
        assertThat(response.getForecast()).isNotNull();
        assertThat(response.getReportScope()).isEqualTo("WORKSPACE");
        assertThat(response.getTemplateType()).isEqualTo("GROWTH_SNAPSHOT");
    }

    private ClientReportService createService(WorkspaceClientReportLinkRepo linkRepo,
                                              WorkspaceClientReportScheduleRepo scheduleRepo,
                                              WorkspaceCapabilityService capabilityService,
                                              ClientReportTokenService tokenService,
                                              AnalyticsWorkspaceService analyticsWorkspaceService,
                                              WorkspaceRepo workspaceRepo,
                                              CompanyRepo companyRepo,
                                              PostCollectionRepo postCollectionRepo,
                                              StorageService storageService,
                                              EmailService emailService) {
        ClientReportService service = new ClientReportService();
        ReflectionTestUtils.setField(service, "workspaceClientReportLinkRepo", linkRepo);
        ReflectionTestUtils.setField(service, "workspaceClientReportScheduleRepo", scheduleRepo);
        ReflectionTestUtils.setField(service, "workspaceCapabilityService", capabilityService);
        ReflectionTestUtils.setField(service, "clientReportTokenService", tokenService);
        ReflectionTestUtils.setField(service, "analyticsWorkspaceService", analyticsWorkspaceService);
        ReflectionTestUtils.setField(service, "workspaceRepo", workspaceRepo);
        ReflectionTestUtils.setField(service, "companyRepo", companyRepo);
        ReflectionTestUtils.setField(service, "postCollectionRepo", postCollectionRepo);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        ReflectionTestUtils.setField(service, "emailService", emailService);
        ReflectionTestUtils.setField(service, "clientReportPdfService", mock(ClientReportPdfService.class));
        ReflectionTestUtils.setField(service, "appBaseUrl", "https://app.socialraven.test");
        return service;
    }

    private ClientReportTokenService createTokenService() {
        ClientReportTokenService service = new ClientReportTokenService();
        ReflectionTestUtils.setField(service, "clientReportSecret", "unit-test-client-report-secret");
        return service;
    }

    private void mockWorkspaceAnalytics(AnalyticsWorkspaceService analyticsWorkspaceService, Long campaignId) {
        OffsetDateTime start = OffsetDateTime.parse("2026-04-01T00:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-30T23:59:59Z");

        when(analyticsWorkspaceService.getOverview(eq(30), isNull(), isNull(), eq(campaignId), isNull()))
                .thenReturn(new AnalyticsWorkspaceOverviewResponse(
                        "Last 30 days",
                        "Previous 30 days",
                        start,
                        end,
                        start.minusDays(30),
                        start.minusSeconds(1),
                        List.of(
                                new AnalyticsOverviewMetricResponse("impressions", "Impressions", "number", 18000, 12000, 6000, 50.0),
                                new AnalyticsOverviewMetricResponse("engagements", "Engagements", "number", 401, 320, 81, 25.3),
                                new AnalyticsOverviewMetricResponse("engagementRate", "Engagement Rate", "percent", 4.12, 3.88, 0.24, 6.19),
                                new AnalyticsOverviewMetricResponse("clicks", "Clicks", "number", 55, 40, 15, 37.5),
                                new AnalyticsOverviewMetricResponse("videoViews", "Video Views", "number", 0, 0, 0, null),
                                new AnalyticsOverviewMetricResponse("postsPublished", "Posts Published", "number", 19, 14, 5, 35.71)
                        )
                ));

        when(analyticsWorkspaceService.getTrendExplorer(eq(30), isNull(), isNull(), eq(campaignId), isNull(), eq("engagements")))
                .thenReturn(new AnalyticsTrendExplorerResponse(
                        "Last 30 days",
                        start,
                        end,
                        "engagements",
                        "Engagements",
                        "number",
                        401,
                        19,
                        21.1,
                        List.of(
                                new AnalyticsTrendExplorerPointResponse("2026-04-01", LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-01"), 66, 3, 22.0),
                                new AnalyticsTrendExplorerPointResponse("2026-04-02", LocalDate.parse("2026-04-02"), LocalDate.parse("2026-04-02"), 82, 4, 20.5)
                        ),
                        List.of()
                ));

        when(analyticsWorkspaceService.getBreakdownEngine(eq(30), isNull(), isNull(), eq(campaignId), isNull(), eq("platform"), eq("engagements")))
                .thenReturn(new AnalyticsBreakdownResponse(
                        "Last 30 days",
                        start,
                        end,
                        "platform",
                        "Platform",
                        "engagements",
                        "Engagements",
                        "number",
                        19,
                        401,
                        21.1,
                        List.of(
                                new AnalyticsBreakdownRowResponse("INSTAGRAM", "Instagram", 11, 280, 57.9, 69.8, 11.9, 25.4),
                                new AnalyticsBreakdownRowResponse("LINKEDIN", "LinkedIn", 8, 121, 42.1, 30.2, -11.9, 15.1)
                        )
                ));

        when(analyticsWorkspaceService.getBreakdownEngine(eq(30), isNull(), isNull(), eq(campaignId), isNull(), eq("platform"), eq("impressions")))
                .thenReturn(new AnalyticsBreakdownResponse(
                        "Last 30 days",
                        start,
                        end,
                        "platform",
                        "Platform",
                        "impressions",
                        "Impressions",
                        "number",
                        19,
                        18000,
                        947.4,
                        List.of(
                                new AnalyticsBreakdownRowResponse("INSTAGRAM", "Instagram", 11, 11000, 57.9, 61.1, 3.2, 1000.0),
                                new AnalyticsBreakdownRowResponse("LINKEDIN", "LinkedIn", 8, 7000, 42.1, 38.9, -3.2, 875.0)
                        )
                ));

        when(analyticsWorkspaceService.getPostRankings(eq(30), isNull(), isNull(), eq(campaignId), isNull(), eq("engagements"), eq(5)))
                .thenReturn(new AnalyticsPostRankingsResponse(
                        "engagements",
                        List.of(new AnalyticsPostRowResponse(
                                91L,
                                "INSTAGRAM",
                                "acct_1",
                                "insta_91",
                                "Orbit Foods",
                                campaignId,
                                campaignId != null ? "April launch push" : null,
                                "A concise campaign clip that outperformed the rest of the month.",
                                "VIDEO",
                                "SHORT_VIDEO",
                                "FRESH",
                                OffsetDateTime.parse("2026-04-02T09:00:00Z"),
                                OffsetDateTime.parse("2026-04-02T12:00:00Z"),
                                true,
                                List.of(),
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
                        )),
                        List.of()
                ));

        when(analyticsWorkspaceService.getForecastPanel(eq(30), isNull(), isNull(), eq(campaignId), isNull(), eq("engagements"), eq(7), eq(3)))
                .thenReturn(new AnalyticsForecastPanelResponse(
                        "Last 30 days",
                        start,
                        end,
                        "engagements",
                        "Engagements",
                        "number",
                        7,
                        "Next 7 days",
                        3,
                        5,
                        3,
                        19,
                        0,
                        "Forecasts use only fresh posts with live metric coverage in the current workspace slice.",
                        new AnalyticsForecastPredictionResponse(
                                true,
                                "HIGH",
                                19,
                                new AnalyticsForecastRangeResponse(180.0, 240.0, 310.0),
                                "Based on comparable posts in the same slice.",
                                null
                        ),
                        new AnalyticsForecastBestSlotResponse(
                                true,
                                "2026-05-05T09",
                                "Mon 09:00 UTC",
                                OffsetDateTime.parse("2026-05-05T09:00:00Z"),
                                "MEDIUM",
                                12,
                                200.0,
                                255.0,
                                27.5,
                                new AnalyticsForecastRangeResponse(210.0, 255.0, 315.0),
                                "Best slot is based on recent slot-level lift.",
                                null
                        ),
                        new AnalyticsEndOfPeriodForecastResponse(
                                true,
                                7,
                                "Next 7 days",
                                3,
                                0.63,
                                "MEDIUM",
                                19,
                                new AnalyticsForecastRangeResponse(520.0, 680.0, 790.0),
                                "Projection assumes similar posting volume and recent performance.",
                                null
                        )
                ));

        if (campaignId != null) {
            when(analyticsWorkspaceService.getCampaignDrilldown(eq(30), isNull(), isNull(), isNull(), eq(campaignId), eq("engagements")))
                    .thenReturn(new com.tonyghouse.socialraven.dto.analytics.AnalyticsCampaignDrilldownResponse(
                            "Last 30 days",
                            start,
                            end,
                            "engagements",
                            "Engagements",
                            "number",
                            campaignId,
                            "April launch push",
                            new AnalyticsComparableBenchmarkResponse(
                                    "Campaigns in this slice",
                                    "Average engagements per post",
                                    4,
                                    255.0,
                                    180.0,
                                    210.0,
                                    41.7
                            ),
                            new AnalyticsPercentileRankResponse(82.0, 1, 4),
                            new AnalyticsDrilldownSummaryResponse(String.valueOf(campaignId), "April launch push", 11, 280.0, 25.4, 57.9, 69.8, 11.9),
                            List.of(new AnalyticsTrendExplorerPointResponse("2026-04-01", LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-01"), 66, 3, 22.0)),
                            new AnalyticsDrilldownContributionResponse(
                                    "platform",
                                    "Platform",
                                    List.of(new AnalyticsBreakdownRowResponse("INSTAGRAM", "Instagram", 11, 280.0, 100.0, 100.0, 0.0, 25.4))
                            ),
                            new AnalyticsDrilldownContributionResponse(
                                    "account",
                                    "Account",
                                    List.of(new AnalyticsBreakdownRowResponse("INSTAGRAM:acct_1", "Orbit Foods", 11, 280.0, 100.0, 100.0, 0.0, 25.4))
                            ),
                            List.of()
                    ));
        }
    }

    private WorkspaceEntity workspace(String workspaceId, String companyId, String name) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setId(workspaceId);
        entity.setCompanyId(companyId);
        entity.setName(name);
        entity.setLogoS3Key(null);
        return entity;
    }

    private CompanyEntity company(String companyId, String name) {
        CompanyEntity entity = new CompanyEntity();
        entity.setId(companyId);
        entity.setName(name);
        entity.setLogoS3Key("logos/agency.png");
        return entity;
    }

    private PostCollectionEntity campaign(String workspaceId, Long id, String description) {
        PostCollectionEntity entity = new PostCollectionEntity();
        entity.setId(id);
        entity.setWorkspaceId(workspaceId);
        entity.setDescription(description);
        return entity;
    }
}
