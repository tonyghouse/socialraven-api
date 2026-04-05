package com.tonyghouse.socialraven.service.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.ClientReportTemplate;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewResponse;
import com.tonyghouse.socialraven.dto.analytics.PlatformStatsResponse;
import com.tonyghouse.socialraven.dto.analytics.TimelinePointResponse;
import com.tonyghouse.socialraven.dto.analytics.TopPostResponse;
import com.tonyghouse.socialraven.dto.reporting.CreateClientReportLinkRequest;
import com.tonyghouse.socialraven.dto.reporting.CreateClientReportScheduleRequest;
import com.tonyghouse.socialraven.dto.reporting.PublicClientReportResponse;
import com.tonyghouse.socialraven.entity.CompanyEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientReportLinkEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientReportScheduleEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.CompanyRepo;
import com.tonyghouse.socialraven.repo.WorkspaceClientReportLinkRepo;
import com.tonyghouse.socialraven.repo.WorkspaceClientReportScheduleRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.EmailService;
import com.tonyghouse.socialraven.service.analytics.AnalyticsApiService;
import com.tonyghouse.socialraven.service.storage.StorageService;
import com.tonyghouse.socialraven.service.workspace.WorkspaceCapabilityService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.Duration;
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
        AnalyticsApiService analyticsApiService = mock(AnalyticsApiService.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);
        StorageService storageService = mock(StorageService.class);
        EmailService emailService = mock(EmailService.class);

        ClientReportService service = createService(
                linkRepo,
                scheduleRepo,
                capabilityService,
                tokenService,
                analyticsApiService,
                workspaceRepo,
                companyRepo,
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
        when(analyticsApiService.getOverview("client-report", 30))
                .thenReturn(new AnalyticsOverviewResponse(12000, 9000, 320, 54, 21, 0, 88, 14, 3.29));
        when(analyticsApiService.getPlatformStats("client-report", 30))
                .thenReturn(List.of(new PlatformStatsResponse("LINKEDIN", 7000, 5100, 210, 31, 12, 9, 0, 40, 8, 3.61)));
        when(analyticsApiService.getTopPosts("client-report", 30, "T30D"))
                .thenReturn(List.of(new TopPostResponse(
                        77L,
                        "LINKEDIN",
                        "post_77",
                        "Performance creative that resonated with client decision-makers.",
                        OffsetDateTime.parse("2026-04-01T12:00:00Z"),
                        "T30D",
                        4200,
                        3300,
                        133,
                        19,
                        8,
                        3.81
                )));
        when(analyticsApiService.getTimeline("client-report", 30))
                .thenReturn(List.of(new TimelinePointResponse("2026-04-01", "LINKEDIN", 42L)));
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
        assertThat(saved.getRecipientEmail()).isEqualTo("ava@client.com");
        assertThat(response.isActive()).isTrue();
        assertThat(response.getAgencyLabel()).isEqualTo("Northshore Agency");

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
    void createScheduleComputesFutureNextSendAt() {
        WorkspaceClientReportLinkRepo linkRepo = mock(WorkspaceClientReportLinkRepo.class);
        WorkspaceClientReportScheduleRepo scheduleRepo = mock(WorkspaceClientReportScheduleRepo.class);
        WorkspaceCapabilityService capabilityService = mock(WorkspaceCapabilityService.class);
        AnalyticsApiService analyticsApiService = mock(AnalyticsApiService.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);

        ClientReportService service = createService(
                linkRepo,
                scheduleRepo,
                capabilityService,
                createTokenService(),
                analyticsApiService,
                workspaceRepo,
                companyRepo,
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

        var response = service.createSchedule("user_1", request);

        assertThat(response.getId()).isEqualTo(41L);
        assertThat(response.getCadence()).isEqualTo("WEEKLY");
        assertThat(response.getTemplateType()).isEqualTo("ENGAGEMENT_SPOTLIGHT");
        assertThat(response.getNextSendAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void createLinkRejectsExpiryBeyondThirtyOneDays() {
        WorkspaceClientReportLinkRepo linkRepo = mock(WorkspaceClientReportLinkRepo.class);
        WorkspaceClientReportScheduleRepo scheduleRepo = mock(WorkspaceClientReportScheduleRepo.class);
        WorkspaceCapabilityService capabilityService = mock(WorkspaceCapabilityService.class);
        AnalyticsApiService analyticsApiService = mock(AnalyticsApiService.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);

        ClientReportService service = createService(
                linkRepo,
                scheduleRepo,
                capabilityService,
                createTokenService(),
                analyticsApiService,
                workspaceRepo,
                companyRepo,
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
        request.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(32));

        assertThatThrownBy(() -> service.createLink("user_1", request))
                .isInstanceOf(SocialRavenException.class)
                .hasMessage("expiresAt cannot be more than 31 days in the future");
    }

    @Test
    void getPublicReportBuildsBrandingAndHighlights() {
        WorkspaceClientReportLinkRepo linkRepo = mock(WorkspaceClientReportLinkRepo.class);
        WorkspaceClientReportScheduleRepo scheduleRepo = mock(WorkspaceClientReportScheduleRepo.class);
        WorkspaceCapabilityService capabilityService = mock(WorkspaceCapabilityService.class);
        ClientReportTokenService tokenService = createTokenService();
        AnalyticsApiService analyticsApiService = mock(AnalyticsApiService.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);
        StorageService storageService = mock(StorageService.class);

        ClientReportService service = createService(
                linkRepo,
                scheduleRepo,
                capabilityService,
                tokenService,
                analyticsApiService,
                workspaceRepo,
                companyRepo,
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
        when(analyticsApiService.getOverview("client-report", 30))
                .thenReturn(new AnalyticsOverviewResponse(18000, 12000, 401, 66, 28, 0, 124, 19, 4.12));
        when(analyticsApiService.getPlatformStats("client-report", 30))
                .thenReturn(List.of(
                        new PlatformStatsResponse("INSTAGRAM", 11000, 8000, 280, 44, 19, 0, 0, 91, 11, 4.17),
                        new PlatformStatsResponse("LINKEDIN", 7000, 4000, 121, 22, 9, 0, 0, 33, 8, 3.44)
                ));
        when(analyticsApiService.getTopPosts("client-report", 30, "T30D"))
                .thenReturn(List.of(new TopPostResponse(
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
                )));
        when(analyticsApiService.getTimeline("client-report", 30))
                .thenReturn(List.of(
                        new TimelinePointResponse("2026-04-01", "INSTAGRAM", 66L),
                        new TimelinePointResponse("2026-04-02", "INSTAGRAM", 82L)
                ));

        String token = tokenService.generateToken("link_1", link.getExpiresAt());
        PublicClientReportResponse response = service.getPublicReport(token);

        assertThat(response.getAgencyLabel()).isEqualTo("Northshore Agency");
        assertThat(response.getLogoUrl()).isEqualTo("https://cdn.socialraven.test/agency.png");
        assertThat(response.getHighlights()).isNotEmpty();
        assertThat(response.getHighlights().get(0)).contains("Delivered 18000 impressions");
        assertThat(response.getTemplateType()).isEqualTo("GROWTH_SNAPSHOT");
    }

    private ClientReportService createService(WorkspaceClientReportLinkRepo linkRepo,
                                              WorkspaceClientReportScheduleRepo scheduleRepo,
                                              WorkspaceCapabilityService capabilityService,
                                              ClientReportTokenService tokenService,
                                              AnalyticsApiService analyticsApiService,
                                              WorkspaceRepo workspaceRepo,
                                              CompanyRepo companyRepo,
                                              StorageService storageService,
                                              EmailService emailService) {
        ClientReportService service = new ClientReportService();
        ReflectionTestUtils.setField(service, "workspaceClientReportLinkRepo", linkRepo);
        ReflectionTestUtils.setField(service, "workspaceClientReportScheduleRepo", scheduleRepo);
        ReflectionTestUtils.setField(service, "workspaceCapabilityService", capabilityService);
        ReflectionTestUtils.setField(service, "clientReportTokenService", tokenService);
        ReflectionTestUtils.setField(service, "analyticsApiService", analyticsApiService);
        ReflectionTestUtils.setField(service, "workspaceRepo", workspaceRepo);
        ReflectionTestUtils.setField(service, "companyRepo", companyRepo);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        ReflectionTestUtils.setField(service, "emailService", emailService);
        ReflectionTestUtils.setField(service, "appBaseUrl", "https://app.socialraven.test");
        return service;
    }

    private ClientReportTokenService createTokenService() {
        ClientReportTokenService service = new ClientReportTokenService();
        ReflectionTestUtils.setField(service, "clientReportSecret", "unit-test-client-report-secret");
        return service;
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
}
