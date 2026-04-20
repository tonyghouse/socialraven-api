package com.tonyghouse.socialraven.service.reporting;

import com.tonyghouse.socialraven.constant.ClientReportCadence;
import com.tonyghouse.socialraven.constant.ClientReportScope;
import com.tonyghouse.socialraven.constant.ClientReportTemplate;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsBreakdownResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsBreakdownRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsCampaignDrilldownResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsDrilldownContributionResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsForecastPanelResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsForecastRangeResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewMetricResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostRankingsResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTrendExplorerPointResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTrendExplorerResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsWorkspaceOverviewResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportCampaignInsightResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportContributionResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportContributionRowResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportForecastItemResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportForecastRangeResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportForecastSummaryResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportLinkResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportPlatformPerformanceResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportScheduleResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportSnapshotRequest;
import com.tonyghouse.socialraven.dto.reporting.ClientReportSummaryResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportTopPostResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportTrendPointResponse;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClientReportService {

    private static final Logger log = LoggerFactory.getLogger(ClientReportService.class);
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int DEFAULT_REPORT_DAYS = 30;
    private static final int MIN_REPORT_DAYS = 7;
    private static final int MAX_REPORT_DAYS = 365;
    private static final int DEFAULT_SHARE_EXPIRY_HOURS = 24 * 14;
    private static final int MAX_SHARE_EXPIRY_HOURS = 24 * 31;
    private static final int DEFAULT_REPORT_HOUR_UTC = 8;
    private static final int DEFAULT_FORECAST_DAYS = 7;
    private static final int DEFAULT_FORECAST_PLANNED_POSTS = 3;
    private static final String DEFAULT_REPORT_METRIC = "engagements";

    @Autowired
    private WorkspaceClientReportLinkRepo workspaceClientReportLinkRepo;

    @Autowired
    private WorkspaceClientReportScheduleRepo workspaceClientReportScheduleRepo;

    @Autowired
    private WorkspaceCapabilityService workspaceCapabilityService;

    @Autowired
    private ClientReportTokenService clientReportTokenService;

    @Autowired
    private AnalyticsWorkspaceService analyticsWorkspaceService;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private CompanyRepo companyRepo;

    @Autowired
    private PostCollectionRepo postCollectionRepo;

    @Autowired
    private StorageService storageService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ClientReportPdfService clientReportPdfService;

    @Value("${socialraven.app.base-url:https://socialraven.io}")
    private String appBaseUrl;

    @Transactional(readOnly = true)
    public PublicClientReportResponse getSnapshot(String userId, ClientReportSnapshotRequest request) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole role = WorkspaceContext.getRole();
        assertCanExportClientReports(workspaceId, userId, role);

        WorkspaceSnapshot snapshot = loadWorkspaceSnapshot(workspaceId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ReportDefinition report = buildReportDefinition(
                snapshot,
                request != null ? request.getReportTitle() : null,
                request != null ? request.getClientLabel() : null,
                request != null ? request.getAgencyLabel() : null,
                request != null ? request.getReportScope() : null,
                request != null ? request.getCampaignId() : null,
                request != null ? request.getTemplateType() : null,
                request != null ? request.getReportDays() : null,
                request != null ? request.getCommentary() : null,
                true
        );

        return buildReportSnapshot(
                report,
                snapshot,
                normalizeLinkExpiry(request != null ? request.getExpiresAt() : null, now),
                now
        );
    }

    @Transactional(readOnly = true)
    public List<ClientReportLinkResponse> getLinks(String userId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole role = WorkspaceContext.getRole();
        assertCanExportClientReports(workspaceId, userId, role);

        return workspaceClientReportLinkRepo.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(this::toLinkResponse)
                .toList();
    }

    @Transactional
    public ClientReportLinkResponse createLink(String userId, CreateClientReportLinkRequest request) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole role = WorkspaceContext.getRole();
        assertCanExportClientReports(workspaceId, userId, role);

        WorkspaceSnapshot snapshot = loadWorkspaceSnapshot(workspaceId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ReportDefinition report = buildReportDefinition(
                snapshot,
                request != null ? request.getReportTitle() : null,
                request != null ? request.getClientLabel() : null,
                request != null ? request.getAgencyLabel() : null,
                request != null ? request.getReportScope() : null,
                request != null ? request.getCampaignId() : null,
                request != null ? request.getTemplateType() : null,
                request != null ? request.getReportDays() : null,
                request != null ? request.getCommentary() : null,
                true
        );

        WorkspaceClientReportLinkEntity link = buildLinkEntity(
                snapshot,
                userId,
                null,
                report,
                request != null ? request.getRecipientName() : null,
                request != null ? request.getRecipientEmail() : null,
                normalizeLinkExpiry(request != null ? request.getExpiresAt() : null, now),
                now
        );

        WorkspaceClientReportLinkEntity saved = workspaceClientReportLinkRepo.save(link);
        sendReportLinkEmailIfNeeded(saved, snapshot, now);
        return toLinkResponse(saved);
    }

    @Transactional
    public void revokeLink(String userId, String linkId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole role = WorkspaceContext.getRole();
        assertCanExportClientReports(workspaceId, userId, role);

        WorkspaceClientReportLinkEntity link = workspaceClientReportLinkRepo.findByIdAndWorkspaceId(linkId, workspaceId)
                .orElseThrow(() -> new SocialRavenException("Client report link not found", HttpStatus.NOT_FOUND));
        if (link.getRevokedAt() != null) {
            return;
        }

        link.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
        link.setRevokedByUserId(userId);
        workspaceClientReportLinkRepo.save(link);
    }

    @Transactional(readOnly = true)
    public List<ClientReportScheduleResponse> getSchedules(String userId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole role = WorkspaceContext.getRole();
        assertCanExportClientReports(workspaceId, userId, role);

        return workspaceClientReportScheduleRepo.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(this::toScheduleResponse)
                .toList();
    }

    @Transactional
    public ClientReportScheduleResponse createSchedule(String userId, CreateClientReportScheduleRequest request) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole role = WorkspaceContext.getRole();
        assertCanExportClientReports(workspaceId, userId, role);

        WorkspaceSnapshot snapshot = loadWorkspaceSnapshot(workspaceId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ClientReportCadence cadence = normalizeCadence(request != null ? request.getCadence() : null);
        Integer hourOfDayUtc = normalizeHourOfDay(request != null ? request.getHourOfDayUtc() : null);
        Integer shareExpiryHours = normalizeShareExpiryHours(request != null ? request.getShareExpiryHours() : null);
        Integer dayOfWeek = cadence == ClientReportCadence.WEEKLY
                ? normalizeDayOfWeek(request != null ? request.getDayOfWeek() : null, now)
                : null;
        Integer dayOfMonth = cadence == ClientReportCadence.MONTHLY
                ? normalizeDayOfMonth(request != null ? request.getDayOfMonth() : null, now)
                : null;
        ReportDefinition report = buildReportDefinition(
                snapshot,
                request != null ? request.getReportTitle() : null,
                request != null ? request.getClientLabel() : null,
                request != null ? request.getAgencyLabel() : null,
                request != null ? request.getReportScope() : null,
                request != null ? request.getCampaignId() : null,
                request != null ? request.getTemplateType() : null,
                request != null ? request.getReportDays() : null,
                request != null ? request.getCommentary() : null,
                true
        );

        WorkspaceClientReportScheduleEntity schedule = new WorkspaceClientReportScheduleEntity();
        schedule.setWorkspaceId(workspaceId);
        schedule.setCreatedByUserId(userId);
        schedule.setReportTitle(report.reportTitle());
        schedule.setRecipientName(normalizeOptionalText(request != null ? request.getRecipientName() : null, 255));
        schedule.setRecipientEmail(normalizeEmail(request != null ? request.getRecipientEmail() : null, true));
        schedule.setClientLabel(report.clientLabel());
        schedule.setAgencyLabel(report.agencyLabel());
        schedule.setReportScope(report.target().scope());
        schedule.setCampaignId(report.target().campaignId());
        schedule.setTemplateType(report.template());
        schedule.setReportDays(report.reportDays());
        schedule.setCommentary(report.commentary());
        schedule.setCadence(cadence);
        schedule.setDayOfWeek(dayOfWeek);
        schedule.setDayOfMonth(dayOfMonth);
        schedule.setHourOfDayUtc(hourOfDayUtc);
        schedule.setShareExpiryHours(shareExpiryHours);
        schedule.setActive(true);
        schedule.setCreatedAt(now);
        schedule.setUpdatedAt(now);
        schedule.setNextSendAt(computeNextSendAt(cadence, dayOfWeek, dayOfMonth, hourOfDayUtc, now));

        WorkspaceClientReportScheduleEntity saved = workspaceClientReportScheduleRepo.save(schedule);
        return toScheduleResponse(saved);
    }

    @Transactional
    public void deactivateSchedule(String userId, Long scheduleId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole role = WorkspaceContext.getRole();
        assertCanExportClientReports(workspaceId, userId, role);

        WorkspaceClientReportScheduleEntity schedule =
                workspaceClientReportScheduleRepo.findByIdAndWorkspaceId(scheduleId, workspaceId)
                        .orElseThrow(() -> new SocialRavenException("Client report schedule not found", HttpStatus.NOT_FOUND));
        if (!schedule.isActive()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        schedule.setActive(false);
        schedule.setDeactivatedAt(now);
        schedule.setUpdatedAt(now);
        workspaceClientReportScheduleRepo.save(schedule);
    }

    @Transactional
    public PublicClientReportResponse getPublicReport(String token) {
        ResolvedClientReportLink resolved = resolvePublicLink(token);
        markLastAccessed(resolved.link());
        ReportDefinition report = buildReportDefinition(
                resolved.workspaceSnapshot(),
                resolved.link().getReportTitle(),
                resolved.link().getClientLabel(),
                resolved.link().getAgencyLabel(),
                safeReportScope(resolved.link().getReportScope()).name(),
                resolved.link().getCampaignId(),
                resolved.link().getTemplateType() != null ? resolved.link().getTemplateType().name() : null,
                resolved.link().getReportDays(),
                resolved.link().getCommentary(),
                false
        );
        return buildReportSnapshot(report, resolved.workspaceSnapshot(), resolved.tokenExpiresAt(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public ClientReportPdfDocument getPublicReportPdf(String token) {
        ResolvedClientReportLink resolved = resolvePublicLink(token);
        markLastAccessed(resolved.link());
        ReportDefinition report = buildReportDefinition(
                resolved.workspaceSnapshot(),
                resolved.link().getReportTitle(),
                resolved.link().getClientLabel(),
                resolved.link().getAgencyLabel(),
                safeReportScope(resolved.link().getReportScope()).name(),
                resolved.link().getCampaignId(),
                resolved.link().getTemplateType() != null ? resolved.link().getTemplateType().name() : null,
                resolved.link().getReportDays(),
                resolved.link().getCommentary(),
                false
        );
        PublicClientReportResponse snapshot = buildReportSnapshot(
                report,
                resolved.workspaceSnapshot(),
                resolved.tokenExpiresAt(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        return new ClientReportPdfDocument(
                clientReportPdfService.render(snapshot),
                buildPdfFileName(snapshot)
        );
    }

    @Transactional
    public void processDueSchedules() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<WorkspaceClientReportScheduleEntity> schedules =
                workspaceClientReportScheduleRepo.findAllByActiveTrueAndNextSendAtLessThanEqualOrderByNextSendAtAsc(now);

        for (WorkspaceClientReportScheduleEntity schedule : schedules) {
            try {
                deliverScheduledReport(schedule, now);
            } catch (Exception ex) {
                log.error("Failed to deliver client report scheduleId={}", schedule.getId(), ex);
            }
        }
    }

    private void deliverScheduledReport(WorkspaceClientReportScheduleEntity schedule, OffsetDateTime now) {
        WorkspaceSnapshot snapshot = loadWorkspaceSnapshot(schedule.getWorkspaceId());
        ReportDefinition report = buildReportDefinition(
                snapshot,
                schedule.getReportTitle(),
                schedule.getClientLabel(),
                schedule.getAgencyLabel(),
                safeReportScope(schedule.getReportScope()).name(),
                schedule.getCampaignId(),
                schedule.getTemplateType() != null ? schedule.getTemplateType().name() : null,
                schedule.getReportDays(),
                schedule.getCommentary(),
                false
        );
        WorkspaceClientReportLinkEntity link = buildLinkEntity(
                snapshot,
                schedule.getCreatedByUserId(),
                schedule.getId(),
                report,
                schedule.getRecipientName(),
                schedule.getRecipientEmail(),
                now.plusHours(schedule.getShareExpiryHours()),
                now
        );

        WorkspaceClientReportLinkEntity saved = workspaceClientReportLinkRepo.save(link);
        sendReportLinkEmail(saved, snapshot, now);

        schedule.setLastSentAt(now);
        schedule.setNextSendAt(computeNextSendAt(
                schedule.getCadence(),
                schedule.getDayOfWeek(),
                schedule.getDayOfMonth(),
                schedule.getHourOfDayUtc(),
                now.plusMinutes(1)
        ));
        schedule.setUpdatedAt(now);
        workspaceClientReportScheduleRepo.save(schedule);
    }

    private ResolvedClientReportLink resolvePublicLink(String token) {
        ClientReportTokenService.ValidatedClientReportToken validated = clientReportTokenService.parseAndValidate(token);
        WorkspaceClientReportLinkEntity link = workspaceClientReportLinkRepo.findById(validated.linkId())
                .orElseThrow(() -> new SocialRavenException("Client report link not found", HttpStatus.NOT_FOUND));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (link.getRevokedAt() != null) {
            throw new SocialRavenException("This client report link has been revoked", HttpStatus.GONE);
        }
        if (!validated.expiresAt().isAfter(now) || !link.getExpiresAt().isAfter(now)) {
            throw new SocialRavenException("This client report link has expired", HttpStatus.GONE);
        }

        return new ResolvedClientReportLink(
                link,
                loadWorkspaceSnapshot(link.getWorkspaceId()),
                validated.expiresAt()
        );
    }

    private PublicClientReportResponse buildReportSnapshot(ReportDefinition report,
                                                           WorkspaceSnapshot snapshot,
                                                           OffsetDateTime linkExpiresAt,
                                                           OffsetDateTime generatedAt) {
        Long campaignId = report.target().scope() == ClientReportScope.CAMPAIGN
                ? report.target().campaignId()
                : null;
        AnalyticsWorkspaceOverviewResponse overview = withWorkspaceContext(
                snapshot.workspace().getId(),
                () -> analyticsWorkspaceService.getOverview(report.reportDays(), null, null, campaignId, null)
        );
        AnalyticsTrendExplorerResponse trend = withWorkspaceContext(
                snapshot.workspace().getId(),
                () -> analyticsWorkspaceService.getTrendExplorer(
                        report.reportDays(),
                        null,
                        null,
                        campaignId,
                        null,
                        DEFAULT_REPORT_METRIC
                )
        );
        AnalyticsBreakdownResponse platformEngagementBreakdown = withWorkspaceContext(
                snapshot.workspace().getId(),
                () -> analyticsWorkspaceService.getBreakdownEngine(
                        report.reportDays(),
                        null,
                        null,
                        campaignId,
                        null,
                        "platform",
                        DEFAULT_REPORT_METRIC
                )
        );
        AnalyticsBreakdownResponse platformImpressionBreakdown = withWorkspaceContext(
                snapshot.workspace().getId(),
                () -> analyticsWorkspaceService.getBreakdownEngine(
                        report.reportDays(),
                        null,
                        null,
                        campaignId,
                        null,
                        "platform",
                        "impressions"
                )
        );
        AnalyticsPostRankingsResponse rankings = withWorkspaceContext(
                snapshot.workspace().getId(),
                () -> analyticsWorkspaceService.getPostRankings(
                        report.reportDays(),
                        null,
                        null,
                        campaignId,
                        null,
                        DEFAULT_REPORT_METRIC,
                        5
                )
        );
        AnalyticsForecastPanelResponse forecast = withWorkspaceContext(
                snapshot.workspace().getId(),
                () -> analyticsWorkspaceService.getForecastPanel(
                        report.reportDays(),
                        null,
                        null,
                        campaignId,
                        null,
                        DEFAULT_REPORT_METRIC,
                        DEFAULT_FORECAST_DAYS,
                        DEFAULT_FORECAST_PLANNED_POSTS
                )
        );
        AnalyticsCampaignDrilldownResponse campaignDrilldown = report.target().scope() == ClientReportScope.CAMPAIGN
                ? loadCampaignDrilldown(snapshot.workspace().getId(), report.reportDays(), report.target().campaignId())
                : null;

        ClientReportSummaryResponse summary = buildSummary(overview);
        List<ClientReportPlatformPerformanceResponse> platformPerformance =
                buildPlatformPerformance(platformEngagementBreakdown, platformImpressionBreakdown);
        List<ClientReportTopPostResponse> topPosts = rankings.getTopPosts().stream()
                .map(this::toTopPostResponse)
                .toList();
        List<ClientReportTrendPointResponse> trendPoints = trend.getDaily().stream()
                .map(this::toTrendPointResponse)
                .toList();
        ClientReportForecastSummaryResponse forecastSummary = buildForecastSummary(forecast);
        ClientReportCampaignInsightResponse campaignInsight = buildCampaignInsight(report.target(), campaignDrilldown);
        String reportWindowLabel = buildReportWindowLabel(overview, report.reportDays());
        String commentary = normalizePublicCommentary(
                report.commentary(),
                report.template(),
                summary,
                platformPerformance,
                topPosts,
                forecastSummary,
                reportWindowLabel,
                report.target()
        );

        return new PublicClientReportResponse(
                report.reportTitle(),
                report.clientLabel(),
                report.agencyLabel(),
                snapshot.workspace().getName(),
                snapshot.companyName(),
                resolveLogoUrl(snapshot),
                report.template().name(),
                report.reportDays(),
                reportWindowLabel,
                report.target().scope().name(),
                reportScopeLabel(report.target().scope()),
                report.target().campaignId(),
                report.target().campaignLabel(),
                commentary,
                buildHighlights(report, summary, platformPerformance, topPosts, forecastSummary, campaignInsight, reportWindowLabel),
                generatedAt,
                linkExpiresAt,
                summary,
                platformPerformance,
                topPosts,
                trendPoints,
                forecastSummary,
                campaignInsight
        );
    }

    private void sendReportLinkEmailIfNeeded(WorkspaceClientReportLinkEntity link,
                                             WorkspaceSnapshot snapshot,
                                             OffsetDateTime generatedAt) {
        if (!StringUtils.hasText(link.getRecipientEmail())) {
            return;
        }
        sendReportLinkEmail(link, snapshot, generatedAt);
    }

    private void sendReportLinkEmail(WorkspaceClientReportLinkEntity link,
                                     WorkspaceSnapshot snapshot,
                                     OffsetDateTime generatedAt) {
        ReportDefinition report = buildReportDefinition(
                snapshot,
                link.getReportTitle(),
                link.getClientLabel(),
                link.getAgencyLabel(),
                safeReportScope(link.getReportScope()).name(),
                link.getCampaignId(),
                link.getTemplateType() != null ? link.getTemplateType().name() : null,
                link.getReportDays(),
                link.getCommentary(),
                false
        );
        PublicClientReportResponse snapshotReport = buildReportSnapshot(report, snapshot, link.getExpiresAt(), generatedAt);
        String reportUrl = snapshot.baseUrl() + "/reports/" + clientReportTokenService.generateToken(link.getId(), link.getExpiresAt());
        emailService.sendClientReportEmail(
                link.getRecipientEmail(),
                link.getRecipientName(),
                link.getReportTitle(),
                snapshotReport.getClientLabel(),
                snapshotReport.getAgencyLabel(),
                snapshotReport.getReportWindowLabel(),
                reportUrl,
                snapshotReport.getHighlights(),
                buildEmailCommentary(snapshotReport.getCommentary(), generatedAt)
        );
    }

    private WorkspaceClientReportLinkEntity buildLinkEntity(WorkspaceSnapshot snapshot,
                                                            String createdByUserId,
                                                            Long scheduleId,
                                                            ReportDefinition report,
                                                            String recipientName,
                                                            String recipientEmail,
                                                            OffsetDateTime expiresAt,
                                                            OffsetDateTime now) {
        WorkspaceClientReportLinkEntity link = new WorkspaceClientReportLinkEntity();
        link.setId(java.util.UUID.randomUUID().toString());
        link.setWorkspaceId(snapshot.workspace().getId());
        link.setCreatedByUserId(createdByUserId);
        link.setScheduleId(scheduleId);
        link.setReportTitle(report.reportTitle());
        link.setClientLabel(report.clientLabel());
        link.setAgencyLabel(report.agencyLabel());
        link.setReportScope(report.target().scope());
        link.setCampaignId(report.target().campaignId());
        link.setTemplateType(report.template());
        link.setReportDays(report.reportDays());
        link.setCommentary(report.commentary());
        link.setRecipientName(normalizeOptionalText(recipientName, 255));
        link.setRecipientEmail(normalizeEmail(recipientEmail, false));
        link.setExpiresAt(expiresAt);
        link.setCreatedAt(now);
        return link;
    }

    private ClientReportLinkResponse toLinkResponse(WorkspaceClientReportLinkEntity entity) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean active = entity.getRevokedAt() == null
                && entity.getExpiresAt() != null
                && entity.getExpiresAt().isAfter(now);

        return new ClientReportLinkResponse(
                entity.getId(),
                clientReportTokenService.generateToken(entity.getId(), entity.getExpiresAt()),
                entity.getReportTitle(),
                entity.getClientLabel(),
                entity.getAgencyLabel(),
                safeReportScope(entity.getReportScope()).name(),
                entity.getCampaignId(),
                resolveCampaignLabel(entity.getWorkspaceId(), entity.getCampaignId()),
                entity.getTemplateType() != null ? entity.getTemplateType().name() : null,
                entity.getReportDays(),
                entity.getRecipientName(),
                entity.getRecipientEmail(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getLastAccessedAt(),
                entity.getCreatedAt(),
                active
        );
    }

    private ClientReportScheduleResponse toScheduleResponse(WorkspaceClientReportScheduleEntity entity) {
        return new ClientReportScheduleResponse(
                entity.getId(),
                entity.getReportTitle(),
                entity.getRecipientName(),
                entity.getRecipientEmail(),
                entity.getClientLabel(),
                entity.getAgencyLabel(),
                safeReportScope(entity.getReportScope()).name(),
                entity.getCampaignId(),
                resolveCampaignLabel(entity.getWorkspaceId(), entity.getCampaignId()),
                entity.getTemplateType() != null ? entity.getTemplateType().name() : null,
                entity.getReportDays(),
                entity.getCadence() != null ? entity.getCadence().name() : null,
                entity.getDayOfWeek(),
                entity.getDayOfMonth(),
                entity.getHourOfDayUtc(),
                entity.getShareExpiryHours(),
                entity.isActive(),
                entity.getLastSentAt(),
                entity.getNextSendAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void assertCanExportClientReports(String workspaceId, String userId, WorkspaceRole role) {
        if (!workspaceCapabilityService.hasCapability(
                workspaceId,
                userId,
                role,
                WorkspaceCapability.EXPORT_CLIENT_REPORTS
        )) {
            throw new SocialRavenException("Export client reports capability is required", HttpStatus.FORBIDDEN);
        }
    }

    private WorkspaceSnapshot loadWorkspaceSnapshot(String workspaceId) {
        WorkspaceEntity workspace = workspaceRepo.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));
        CompanyEntity company = companyRepo.findById(workspace.getCompanyId()).orElse(null);
        return new WorkspaceSnapshot(
                workspace,
                company,
                company != null ? company.getName() : workspace.getName(),
                resolveBaseUrl()
        );
    }

    private ReportDefinition buildReportDefinition(WorkspaceSnapshot snapshot,
                                                   String reportTitle,
                                                   String clientLabel,
                                                   String agencyLabel,
                                                   String reportScope,
                                                   Long campaignId,
                                                   String templateType,
                                                   Integer reportDays,
                                                   String commentary,
                                                   boolean validateCampaignExists) {
        Integer normalizedReportDays = normalizeReportDays(reportDays);
        ResolvedReportTarget target = validateCampaignExists
                ? resolveReportTarget(snapshot.workspace().getId(), reportScope, campaignId)
                : restoreReportTarget(snapshot.workspace().getId(), reportScope, campaignId);
        return new ReportDefinition(
                normalizeReportTitle(reportTitle, snapshot.workspace(), normalizedReportDays),
                normalizeOptionalText(
                        clientLabel != null ? clientLabel : snapshot.workspace().getName(),
                        255
                ),
                normalizeOptionalText(
                        agencyLabel != null ? agencyLabel : snapshot.companyName(),
                        255
                ),
                target,
                normalizeTemplate(templateType),
                normalizedReportDays,
                normalizeOptionalText(commentary, 4000)
        );
    }

    private ResolvedReportTarget resolveReportTarget(String workspaceId, String reportScope, Long campaignId) {
        ClientReportScope scope = normalizeReportScope(reportScope);
        if (scope == ClientReportScope.WORKSPACE) {
            return new ResolvedReportTarget(scope, null, null);
        }

        if (campaignId == null) {
            throw new SocialRavenException("campaignId is required when reportScope is CAMPAIGN", HttpStatus.BAD_REQUEST);
        }

        PostCollectionEntity campaign = postCollectionRepo.findByIdAndWorkspaceId(campaignId, workspaceId)
                .orElseThrow(() -> new SocialRavenException("Campaign not found for this workspace", HttpStatus.NOT_FOUND));
        return new ResolvedReportTarget(scope, campaign.getId(), labelForCampaign(campaign));
    }

    private ResolvedReportTarget restoreReportTarget(String workspaceId, String reportScope, Long campaignId) {
        ClientReportScope scope = normalizeReportScope(reportScope);
        if (scope == ClientReportScope.WORKSPACE) {
            return new ResolvedReportTarget(scope, null, null);
        }
        if (campaignId == null) {
            return new ResolvedReportTarget(ClientReportScope.WORKSPACE, null, null);
        }
        return new ResolvedReportTarget(scope, campaignId, resolveCampaignLabel(workspaceId, campaignId));
    }

    private ClientReportSummaryResponse buildSummary(AnalyticsWorkspaceOverviewResponse overview) {
        return new ClientReportSummaryResponse(
                overview.getCurrentRangeLabel(),
                overview.getCurrentStartAt(),
                overview.getCurrentEndAt(),
                roundMetricValue(findMetric(overview, "impressions")),
                roundMetricValue(findMetric(overview, "engagements")),
                findMetric(overview, "engagementRate"),
                roundMetricValue(findMetric(overview, "clicks")),
                roundMetricValue(findMetric(overview, "videoViews")),
                roundMetricValue(findMetric(overview, "postsPublished"))
        );
    }

    private double findMetric(AnalyticsWorkspaceOverviewResponse overview, String key) {
        return overview.getMetrics().stream()
                .filter(metric -> key.equals(metric.getKey()))
                .map(AnalyticsOverviewMetricResponse::getCurrentValue)
                .findFirst()
                .orElse(0.0d);
    }

    private List<ClientReportPlatformPerformanceResponse> buildPlatformPerformance(AnalyticsBreakdownResponse engagementBreakdown,
                                                                                  AnalyticsBreakdownResponse impressionBreakdown) {
        Map<String, ClientReportPlatformPerformanceResponse> merged = new LinkedHashMap<>();

        for (AnalyticsBreakdownRowResponse row : engagementBreakdown.getRows()) {
            merged.put(row.getKey(), new ClientReportPlatformPerformanceResponse(
                    row.getKey(),
                    defaultIfBlank(row.getLabel(), platformLabel(row.getKey())),
                    row.getPostsPublished(),
                    roundMetricValue(row.getPerformanceValue()),
                    0L,
                    row.getAveragePerformancePerPost(),
                    row.getOutputSharePercent(),
                    row.getPerformanceSharePercent(),
                    null
            ));
        }

        for (AnalyticsBreakdownRowResponse row : impressionBreakdown.getRows()) {
            ClientReportPlatformPerformanceResponse current = merged.get(row.getKey());
            if (current == null) {
                current = new ClientReportPlatformPerformanceResponse(
                        row.getKey(),
                        defaultIfBlank(row.getLabel(), platformLabel(row.getKey())),
                        row.getPostsPublished(),
                        0L,
                        0L,
                        null,
                        row.getOutputSharePercent(),
                        null,
                        row.getPerformanceSharePercent()
                );
                merged.put(row.getKey(), current);
            }
            current.setImpressions(roundMetricValue(row.getPerformanceValue()));
            current.setImpressionSharePercent(row.getPerformanceSharePercent());
            if (current.getPostsPublished() == 0L) {
                current.setPostsPublished(row.getPostsPublished());
            }
            if (current.getOutputSharePercent() == null) {
                current.setOutputSharePercent(row.getOutputSharePercent());
            }
        }

        return merged.values().stream()
                .sorted(Comparator.comparingLong(ClientReportPlatformPerformanceResponse::getEngagements)
                        .reversed()
                        .thenComparing(Comparator.comparingLong(ClientReportPlatformPerformanceResponse::getImpressions)
                                .reversed()))
                .toList();
    }

    private ClientReportTopPostResponse toTopPostResponse(AnalyticsPostRowResponse row) {
        return new ClientReportTopPostResponse(
                row.getPostId(),
                row.getProvider(),
                platformLabel(row.getProvider()),
                row.getAccountName(),
                row.getCampaignId(),
                row.getCampaignLabel(),
                row.getContent(),
                row.getPostType(),
                row.getMediaFormat(),
                row.getPublishedAt(),
                row.getImpressions(),
                row.getReach(),
                row.getLikes(),
                row.getComments(),
                row.getShares(),
                row.getSaves(),
                row.getClicks(),
                row.getVideoViews(),
                row.getWatchTimeMinutes(),
                row.getEngagements(),
                row.getEngagementRate()
        );
    }

    private ClientReportTrendPointResponse toTrendPointResponse(AnalyticsTrendExplorerPointResponse point) {
        return new ClientReportTrendPointResponse(
                point.getBucketKey(),
                point.getBucketStartDate(),
                point.getBucketEndDate(),
                point.getPerformanceValue(),
                point.getPostsPublished(),
                point.getAveragePerformancePerPost()
        );
    }

    private ClientReportForecastSummaryResponse buildForecastSummary(AnalyticsForecastPanelResponse forecast) {
        return new ClientReportForecastSummaryResponse(
                forecast.getMetric(),
                forecast.getMetricLabel(),
                forecast.getMetricFormat(),
                forecast.getPlanningWindowLabel(),
                forecast.getBasisNote(),
                new ClientReportForecastItemResponse(
                        forecast.getNextPostForecast().isAvailable(),
                        "Next post prediction",
                        forecast.getNextPostForecast().getConfidenceTier(),
                        null,
                        null,
                        null,
                        forecast.getNextPostForecast().getComparablePosts(),
                        null,
                        toForecastRange(forecast.getNextPostForecast().getRange()),
                        forecast.getNextPostForecast().getBasisSummary(),
                        forecast.getNextPostForecast().getUnavailableReason()
                ),
                new ClientReportForecastItemResponse(
                        forecast.getNextBestSlot().isAvailable(),
                        "Best next slot",
                        forecast.getNextBestSlot().getConfidenceTier(),
                        forecast.getNextBestSlot().getSlotLabel(),
                        forecast.getForecastDays(),
                        1,
                        forecast.getNextBestSlot().getComparablePosts(),
                        forecast.getNextBestSlot().getLiftPercent(),
                        toForecastRange(forecast.getNextBestSlot().getRange()),
                        forecast.getNextBestSlot().getBasisSummary(),
                        forecast.getNextBestSlot().getUnavailableReason()
                ),
                new ClientReportForecastItemResponse(
                        forecast.getEndOfPeriodForecast().isAvailable(),
                        "Planning window projection",
                        forecast.getEndOfPeriodForecast().getConfidenceTier(),
                        null,
                        forecast.getEndOfPeriodForecast().getForecastDays(),
                        forecast.getEndOfPeriodForecast().getPlannedPosts(),
                        forecast.getEndOfPeriodForecast().getComparablePosts(),
                        null,
                        toForecastRange(forecast.getEndOfPeriodForecast().getRange()),
                        forecast.getEndOfPeriodForecast().getBasisSummary(),
                        forecast.getEndOfPeriodForecast().getUnavailableReason()
                )
        );
    }

    private ClientReportForecastRangeResponse toForecastRange(AnalyticsForecastRangeResponse range) {
        if (range == null) {
            return null;
        }
        return new ClientReportForecastRangeResponse(range.getLowValue(), range.getExpectedValue(), range.getHighValue());
    }

    private ClientReportCampaignInsightResponse buildCampaignInsight(ResolvedReportTarget target,
                                                                    AnalyticsCampaignDrilldownResponse drilldown) {
        if (target.scope() != ClientReportScope.CAMPAIGN) {
            return null;
        }

        if (drilldown == null) {
            return new ClientReportCampaignInsightResponse(
                    target.campaignId(),
                    target.campaignLabel(),
                    0L,
                    0.0d,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0L,
                    List.of(),
                    null,
                    null
            );
        }

        return new ClientReportCampaignInsightResponse(
                drilldown.getCampaignId(),
                defaultIfBlank(drilldown.getCampaignLabel(), target.campaignLabel()),
                drilldown.getSummary().getPostsPublished(),
                drilldown.getSummary().getPerformanceValue(),
                drilldown.getSummary().getAveragePerformancePerPost(),
                drilldown.getComparableBenchmark() != null ? drilldown.getComparableBenchmark().getComparableAverageValue() : null,
                drilldown.getComparableBenchmark() != null ? drilldown.getComparableBenchmark().getLiftPercent() : null,
                drilldown.getPercentileRank() != null ? drilldown.getPercentileRank().getPercentile() : null,
                drilldown.getPercentileRank() != null ? drilldown.getPercentileRank().getRank() : null,
                drilldown.getPercentileRank() != null ? drilldown.getPercentileRank().getComparableCount() : 0L,
                drilldown.getTrend().stream().map(this::toTrendPointResponse).toList(),
                toContribution(drilldown.getPlatformBreakdown()),
                toContribution(drilldown.getAccountBreakdown())
        );
    }

    private ClientReportContributionResponse toContribution(AnalyticsDrilldownContributionResponse contribution) {
        if (contribution == null) {
            return null;
        }
        return new ClientReportContributionResponse(
                contribution.getDimension(),
                contribution.getDimensionLabel(),
                contribution.getRows().stream()
                        .map(this::toContributionRow)
                        .toList()
        );
    }

    private ClientReportContributionRowResponse toContributionRow(AnalyticsBreakdownRowResponse row) {
        return new ClientReportContributionRowResponse(
                row.getKey(),
                row.getLabel(),
                row.getPostsPublished(),
                row.getPerformanceValue(),
                row.getOutputSharePercent(),
                row.getPerformanceSharePercent(),
                row.getShareGapPercent(),
                row.getAveragePerformancePerPost()
        );
    }

    private AnalyticsCampaignDrilldownResponse loadCampaignDrilldown(String workspaceId, int reportDays, Long campaignId) {
        try {
            return withWorkspaceContext(
                    workspaceId,
                    () -> analyticsWorkspaceService.getCampaignDrilldown(
                            reportDays,
                            null,
                            null,
                            null,
                            campaignId,
                            DEFAULT_REPORT_METRIC
                    )
            );
        } catch (SocialRavenException ex) {
            if (String.valueOf(HttpStatus.NOT_FOUND.value()).equals(ex.getErrorCode())) {
                return null;
            }
            throw ex;
        }
    }

    private String buildReportWindowLabel(AnalyticsWorkspaceOverviewResponse overview, int reportDays) {
        if (StringUtils.hasText(overview.getCurrentRangeLabel())) {
            return overview.getCurrentRangeLabel();
        }
        return "Last " + reportDays + " days";
    }

    private OffsetDateTime normalizeLinkExpiry(OffsetDateTime requestedExpiry, OffsetDateTime now) {
        OffsetDateTime expiresAt = requestedExpiry != null
                ? requestedExpiry
                : now.plusHours(DEFAULT_SHARE_EXPIRY_HOURS);
        if (!expiresAt.isAfter(now)) {
            throw new SocialRavenException("expiresAt must be in the future", HttpStatus.BAD_REQUEST);
        }
        OffsetDateTime maxAllowedExpiry = now.plusHours(MAX_SHARE_EXPIRY_HOURS);
        if (expiresAt.isAfter(maxAllowedExpiry)) {
            throw new SocialRavenException(
                    "expiresAt cannot be more than 31 days in the future",
                    HttpStatus.BAD_REQUEST
            );
        }
        return expiresAt;
    }

    private String normalizeReportTitle(String reportTitle, WorkspaceEntity workspace, Integer reportDays) {
        String normalized = normalizeOptionalText(reportTitle, 255);
        if (normalized != null) {
            return normalized;
        }
        return workspace.getName() + " performance report (" + reportDays + "d)";
    }

    private ClientReportTemplate normalizeTemplate(String templateType) {
        if (templateType == null || templateType.isBlank()) {
            return ClientReportTemplate.EXECUTIVE_SUMMARY;
        }
        try {
            return ClientReportTemplate.valueOf(templateType.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            throw new SocialRavenException("Unsupported report template", HttpStatus.BAD_REQUEST);
        }
    }

    private ClientReportCadence normalizeCadence(String cadence) {
        if (cadence == null || cadence.isBlank()) {
            return ClientReportCadence.WEEKLY;
        }
        try {
            return ClientReportCadence.valueOf(cadence.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            throw new SocialRavenException("Unsupported report cadence", HttpStatus.BAD_REQUEST);
        }
    }

    private ClientReportScope normalizeReportScope(String reportScope) {
        if (!StringUtils.hasText(reportScope)) {
            return ClientReportScope.WORKSPACE;
        }
        try {
            return ClientReportScope.valueOf(reportScope.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            throw new SocialRavenException("Unsupported report scope", HttpStatus.BAD_REQUEST);
        }
    }

    private ClientReportScope safeReportScope(ClientReportScope reportScope) {
        return reportScope != null ? reportScope : ClientReportScope.WORKSPACE;
    }

    private Integer normalizeReportDays(Integer reportDays) {
        int resolved = reportDays != null ? reportDays : DEFAULT_REPORT_DAYS;
        if (resolved < MIN_REPORT_DAYS || resolved > MAX_REPORT_DAYS) {
            throw new SocialRavenException("reportDays must be between 7 and 365", HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }

    private Integer normalizeShareExpiryHours(Integer shareExpiryHours) {
        int resolved = shareExpiryHours != null ? shareExpiryHours : DEFAULT_SHARE_EXPIRY_HOURS;
        if (resolved < 24 || resolved > MAX_SHARE_EXPIRY_HOURS) {
            throw new SocialRavenException("shareExpiryHours must be between 24 and 744", HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }

    private Integer normalizeHourOfDay(Integer hourOfDayUtc) {
        int resolved = hourOfDayUtc != null ? hourOfDayUtc : DEFAULT_REPORT_HOUR_UTC;
        if (resolved < 0 || resolved > 23) {
            throw new SocialRavenException("hourOfDayUtc must be between 0 and 23", HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }

    private Integer normalizeDayOfWeek(Integer requestedDayOfWeek, OffsetDateTime now) {
        int resolved = requestedDayOfWeek != null ? requestedDayOfWeek : now.getDayOfWeek().getValue();
        if (resolved < 1 || resolved > 7) {
            throw new SocialRavenException("dayOfWeek must be between 1 and 7", HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }

    private Integer normalizeDayOfMonth(Integer requestedDayOfMonth, OffsetDateTime now) {
        int resolved = requestedDayOfMonth != null ? requestedDayOfMonth : Math.min(now.getDayOfMonth(), 28);
        if (resolved < 1 || resolved > 28) {
            throw new SocialRavenException("dayOfMonth must be between 1 and 28", HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new SocialRavenException("Text value exceeds allowed length", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeEmail(String email, boolean required) {
        String normalized = normalizeOptionalText(email, 320);
        if (normalized == null) {
            if (required) {
                throw new SocialRavenException("recipientEmail is required", HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ENGLISH);
        if (!EMAIL_PATTERN.matcher(lower).matches()) {
            throw new SocialRavenException("recipientEmail is invalid", HttpStatus.BAD_REQUEST);
        }
        return lower;
    }

    private OffsetDateTime computeNextSendAt(ClientReportCadence cadence,
                                             Integer dayOfWeek,
                                             Integer dayOfMonth,
                                             Integer hourOfDayUtc,
                                             OffsetDateTime referenceTime) {
        OffsetDateTime now = referenceTime.withOffsetSameInstant(ZoneOffset.UTC);
        if (cadence == ClientReportCadence.MONTHLY) {
            int resolvedDayOfMonth = Objects.requireNonNull(dayOfMonth);
            OffsetDateTime candidate = now.withDayOfMonth(resolvedDayOfMonth)
                    .withHour(hourOfDayUtc)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusMonths(1).withDayOfMonth(resolvedDayOfMonth);
            }
            return candidate;
        }

        int resolvedDayOfWeek = Objects.requireNonNull(dayOfWeek);
        OffsetDateTime candidate = now.withHour(hourOfDayUtc)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        int delta = resolvedDayOfWeek - candidate.getDayOfWeek().getValue();
        if (delta < 0) {
            delta += 7;
        }
        candidate = candidate.plusDays(delta);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate;
    }

    private String normalizePublicCommentary(String requestCommentary,
                                             ClientReportTemplate template,
                                             ClientReportSummaryResponse summary,
                                             List<ClientReportPlatformPerformanceResponse> platformPerformance,
                                             List<ClientReportTopPostResponse> topPosts,
                                             ClientReportForecastSummaryResponse forecast,
                                             String reportWindowLabel,
                                             ResolvedReportTarget target) {
        if (StringUtils.hasText(requestCommentary)) {
            return requestCommentary.trim();
        }

        ClientReportPlatformPerformanceResponse strongestPlatform = platformPerformance.stream().findFirst().orElse(null);
        ClientReportTopPostResponse strongestPost = topPosts.stream().findFirst().orElse(null);
        String scopePhrase = target.scope() == ClientReportScope.CAMPAIGN && StringUtils.hasText(target.campaignLabel())
                ? "campaign " + target.campaignLabel()
                : "workspace";

        return switch (template) {
            case ENGAGEMENT_SPOTLIGHT -> {
                if (strongestPlatform != null) {
                    yield strongestPlatform.getPlatformLabel() + " led engagement for this " + scopePhrase
                            + " during " + reportWindowLabel.toLowerCase(Locale.ENGLISH)
                            + ", producing " + formatWholeNumber(strongestPlatform.getEngagements())
                            + " interactions across published posts.";
                }
                yield "This engagement spotlight summarizes the posts, channels, and publishing windows that delivered the strongest audience response.";
            }
            case GROWTH_SNAPSHOT -> {
                if (forecast != null && forecast.getPlanningWindowProjection() != null
                        && forecast.getPlanningWindowProjection().isAvailable()
                        && forecast.getPlanningWindowProjection().getRange() != null
                        && forecast.getPlanningWindowProjection().getRange().getExpectedValue() != null) {
                    yield "This snapshot focuses on momentum and planning confidence for the " + scopePhrase
                            + ", with the next " + forecast.getPlanningWindowProjection().getForecastDays()
                            + " days projected around "
                            + formatWholeNumber(roundMetricValue(forecast.getPlanningWindowProjection().getRange().getExpectedValue()))
                            + " engagements.";
                }
                yield "This snapshot focuses on momentum, engagement efficiency, and the next publishing opportunities in the current reporting window.";
            }
            case EXECUTIVE_SUMMARY -> {
                if (strongestPost != null && strongestPost.getEngagements() != null) {
                    yield "This executive summary covers " + reportWindowLabel.toLowerCase(Locale.ENGLISH)
                            + " for the " + scopePhrase + ", including "
                            + formatWholeNumber(summary.getEngagements())
                            + " total engagements and the top-performing content that drove the period.";
                }
                yield "This executive summary covers the most important reach, engagement, and publishing signals in the current reporting window.";
            }
        };
    }

    private List<String> buildHighlights(ReportDefinition report,
                                         ClientReportSummaryResponse summary,
                                         List<ClientReportPlatformPerformanceResponse> platformPerformance,
                                         List<ClientReportTopPostResponse> topPosts,
                                         ClientReportForecastSummaryResponse forecast,
                                         ClientReportCampaignInsightResponse campaignInsight,
                                         String reportWindowLabel) {
        List<String> highlights = new ArrayList<>();
        highlights.add("Recorded " + formatWholeNumber(summary.getImpressions())
                + " impressions and " + formatWholeNumber(summary.getEngagements())
                + " engagements across " + formatWholeNumber(summary.getPostsPublished())
                + " published posts in " + reportWindowLabel.toLowerCase(Locale.ENGLISH) + ".");

        platformPerformance.stream().findFirst().ifPresent(platform -> highlights.add(
                platform.getPlatformLabel() + " led the window with "
                        + formatWholeNumber(platform.getEngagements()) + " engagements across "
                        + formatWholeNumber(platform.getPostsPublished()) + " posts"
                        + (platform.getEngagementSharePercent() != null
                        ? ", contributing " + formatPercent(platform.getEngagementSharePercent()) + " of total engagement."
                        : ".")
        ));

        topPosts.stream().findFirst().ifPresent(post -> highlights.add(
                "Top content came from " + platformLabel(post.getProvider())
                        + ", delivering " + formatWholeNumber(valueOrZero(post.getEngagements()))
                        + " engagements from "
                        + formatWholeNumber(valueOrZero(post.getLikes())) + " likes, "
                        + formatWholeNumber(valueOrZero(post.getComments())) + " comments, and "
                        + formatWholeNumber(valueOrZero(post.getShares())) + " shares."
        ));

        if (forecast != null && forecast.getNextBestSlot() != null && forecast.getNextBestSlot().isAvailable()) {
            ClientReportForecastItemResponse bestSlot = forecast.getNextBestSlot();
            highlights.add("Forecasting suggests " + defaultIfBlank(bestSlot.getSlotLabel(), "the next recommended slot")
                    + " can outperform the current slice baseline"
                    + (bestSlot.getLiftPercent() != null
                    ? " by " + formatPercent(bestSlot.getLiftPercent()) + "."
                    : "."));
        } else if (campaignInsight != null && campaignInsight.getPercentile() != null) {
            highlights.add("This campaign ranks in the " + formatWholeNumber(Math.round(campaignInsight.getPercentile()))
                    + "th percentile against comparable campaigns in the same workspace slice.");
        }

        return highlights.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(4)
                .toList();
    }

    private String resolveLogoUrl(WorkspaceSnapshot snapshot) {
        String fileKey = snapshot.workspace().getLogoS3Key();
        if (!StringUtils.hasText(fileKey) && snapshot.company() != null) {
            fileKey = snapshot.company().getLogoS3Key();
        }
        if (!StringUtils.hasText(fileKey)) {
            return null;
        }
        return storageService.generatePresignedGetUrl(fileKey, Duration.ofHours(12));
    }

    private void markLastAccessed(WorkspaceClientReportLinkEntity link) {
        link.setLastAccessedAt(OffsetDateTime.now(ZoneOffset.UTC));
        workspaceClientReportLinkRepo.save(link);
    }

    private <T> T withWorkspaceContext(String workspaceId, Supplier<T> supplier) {
        String previousWorkspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole previousRole = WorkspaceContext.getRole();
        boolean replace = previousWorkspaceId == null || !previousWorkspaceId.equals(workspaceId);

        if (replace) {
            WorkspaceContext.set(workspaceId, WorkspaceRole.READ_ONLY);
        }

        try {
            return supplier.get();
        } finally {
            if (replace) {
                if (previousWorkspaceId != null && previousRole != null) {
                    WorkspaceContext.set(previousWorkspaceId, previousRole);
                } else {
                    WorkspaceContext.clear();
                }
            }
        }
    }

    private String defaultIfBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private String buildEmailCommentary(String reportCommentary, OffsetDateTime generatedAt) {
        String timestamp = generatedAt.format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm 'UTC'", Locale.ENGLISH));
        if (!StringUtils.hasText(reportCommentary)) {
            return "Prepared " + timestamp + ". Open the live report to review the latest metrics, trends, and top-performing content.";
        }
        return reportCommentary + " Prepared " + timestamp + ".";
    }

    private String buildPdfFileName(PublicClientReportResponse report) {
        String candidate = defaultIfBlank(report.getReportTitle(), report.getClientLabel());
        if (!StringUtils.hasText(candidate)) {
            candidate = "client-report";
        }
        String slug = candidate.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            slug = "client-report";
        }
        if (!slug.endsWith("-report")) {
            slug = slug + "-report";
        }
        return slug + ".pdf";
    }

    private String resolveBaseUrl() {
        String trimmed = appBaseUrl == null || appBaseUrl.isBlank()
                ? "https://socialraven.io"
                : appBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String resolveCampaignLabel(String workspaceId, Long campaignId) {
        if (campaignId == null) {
            return null;
        }
        return postCollectionRepo.findByIdAndWorkspaceId(campaignId, workspaceId)
                .map(this::labelForCampaign)
                .orElse("Campaign #" + campaignId);
    }

    private String labelForCampaign(PostCollectionEntity campaign) {
        if (campaign == null || campaign.getId() == null) {
            return "Campaign";
        }
        String description = normalizeOptionalText(campaign.getDescription(), 100000);
        if (!StringUtils.hasText(description)) {
            return "Campaign #" + campaign.getId();
        }
        String firstLine = description.split("\\R", 2)[0].trim();
        if (!StringUtils.hasText(firstLine)) {
            return "Campaign #" + campaign.getId();
        }
        return firstLine.length() > 80 ? firstLine.substring(0, 77) + "..." : firstLine;
    }

    private String reportScopeLabel(ClientReportScope scope) {
        return scope == ClientReportScope.CAMPAIGN ? "Campaign" : "Workspace";
    }

    private String platformLabel(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "Platform";
        }
        try {
            Provider normalized = Provider.valueOf(provider.trim().toUpperCase(Locale.ENGLISH));
            return switch (normalized) {
                case INSTAGRAM -> "Instagram";
                case X -> "X";
                case LINKEDIN -> "LinkedIn";
                case FACEBOOK -> "Facebook";
                case YOUTUBE -> "YouTube";
                case TIKTOK -> "TikTok";
                case THREADS -> "Threads";
            };
        } catch (IllegalArgumentException ex) {
            String compact = provider.trim().toLowerCase(Locale.ENGLISH).replace('_', ' ');
            return compact.substring(0, 1).toUpperCase(Locale.ENGLISH) + compact.substring(1);
        }
    }

    private long roundMetricValue(double value) {
        return Math.round(value);
    }

    private String formatWholeNumber(long value) {
        return String.format(Locale.ENGLISH, "%,d", value);
    }

    private String formatPercent(double value) {
        return String.format(Locale.ENGLISH, "%.1f%%", value);
    }

    private Long valueOrZero(Long value) {
        return value != null ? value : 0L;
    }

    private record WorkspaceSnapshot(WorkspaceEntity workspace,
                                     CompanyEntity company,
                                     String companyName,
                                     String baseUrl) {
    }

    private record ResolvedClientReportLink(WorkspaceClientReportLinkEntity link,
                                            WorkspaceSnapshot workspaceSnapshot,
                                            OffsetDateTime tokenExpiresAt) {
    }

    private record ResolvedReportTarget(ClientReportScope scope,
                                        Long campaignId,
                                        String campaignLabel) {
    }

    private record ReportDefinition(String reportTitle,
                                    String clientLabel,
                                    String agencyLabel,
                                    ResolvedReportTarget target,
                                    ClientReportTemplate template,
                                    Integer reportDays,
                                    String commentary) {
    }

    public record ClientReportPdfDocument(byte[] bytes, String fileName) {
    }
}
