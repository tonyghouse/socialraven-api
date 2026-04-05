package com.tonyghouse.socialraven.service.reporting;

import com.tonyghouse.socialraven.constant.ClientReportCadence;
import com.tonyghouse.socialraven.constant.ClientReportTemplate;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewResponse;
import com.tonyghouse.socialraven.dto.analytics.PlatformStatsResponse;
import com.tonyghouse.socialraven.dto.analytics.TimelinePointResponse;
import com.tonyghouse.socialraven.dto.analytics.TopPostResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportLinkResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportScheduleResponse;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    @Autowired
    private WorkspaceClientReportLinkRepo workspaceClientReportLinkRepo;

    @Autowired
    private WorkspaceClientReportScheduleRepo workspaceClientReportScheduleRepo;

    @Autowired
    private WorkspaceCapabilityService workspaceCapabilityService;

    @Autowired
    private ClientReportTokenService clientReportTokenService;

    @Autowired
    private AnalyticsApiService analyticsApiService;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private CompanyRepo companyRepo;

    @Autowired
    private StorageService storageService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ClientReportPdfService clientReportPdfService;

    @Value("${socialraven.app.base-url:https://socialraven.io}")
    private String appBaseUrl;

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
        WorkspaceClientReportLinkEntity link = buildLinkEntity(
                snapshot,
                userId,
                null,
                request != null ? request.getReportTitle() : null,
                request != null ? request.getClientLabel() : null,
                request != null ? request.getAgencyLabel() : null,
                request != null ? request.getTemplateType() : null,
                request != null ? request.getReportDays() : null,
                request != null ? request.getCommentary() : null,
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
        Integer reportDays = normalizeReportDays(request != null ? request.getReportDays() : null);
        Integer hourOfDayUtc = normalizeHourOfDay(request != null ? request.getHourOfDayUtc() : null);
        Integer shareExpiryHours = normalizeShareExpiryHours(request != null ? request.getShareExpiryHours() : null);
        Integer dayOfWeek = cadence == ClientReportCadence.WEEKLY
                ? normalizeDayOfWeek(request != null ? request.getDayOfWeek() : null, now)
                : null;
        Integer dayOfMonth = cadence == ClientReportCadence.MONTHLY
                ? normalizeDayOfMonth(request != null ? request.getDayOfMonth() : null, now)
                : null;

        WorkspaceClientReportScheduleEntity schedule = new WorkspaceClientReportScheduleEntity();
        schedule.setWorkspaceId(workspaceId);
        schedule.setCreatedByUserId(userId);
        schedule.setReportTitle(normalizeReportTitle(
                request != null ? request.getReportTitle() : null,
                snapshot.workspace(),
                reportDays
        ));
        schedule.setRecipientName(normalizeOptionalText(request != null ? request.getRecipientName() : null, 255));
        schedule.setRecipientEmail(normalizeEmail(request != null ? request.getRecipientEmail() : null, true));
        schedule.setClientLabel(normalizeOptionalText(
                request != null ? request.getClientLabel() : snapshot.workspace().getName(),
                255
        ));
        schedule.setAgencyLabel(normalizeOptionalText(
                request != null ? request.getAgencyLabel() : snapshot.companyName(),
                255
        ));
        schedule.setTemplateType(normalizeTemplate(request != null ? request.getTemplateType() : null));
        schedule.setReportDays(reportDays);
        schedule.setCommentary(normalizeOptionalText(request != null ? request.getCommentary() : null, 4000));
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
        return buildPublicReport(resolved.link(), resolved.workspaceSnapshot(), resolved.tokenExpiresAt());
    }

    @Transactional
    public ClientReportPdfDocument getPublicReportPdf(String token) {
        ResolvedClientReportLink resolved = resolvePublicLink(token);
        markLastAccessed(resolved.link());
        PublicClientReportResponse report = buildPublicReport(
                resolved.link(),
                resolved.workspaceSnapshot(),
                resolved.tokenExpiresAt()
        );
        return new ClientReportPdfDocument(
                clientReportPdfService.render(report),
                buildPdfFileName(report)
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
        WorkspaceClientReportLinkEntity link = buildLinkEntity(
                snapshot,
                schedule.getCreatedByUserId(),
                schedule.getId(),
                schedule.getReportTitle(),
                schedule.getClientLabel(),
                schedule.getAgencyLabel(),
                schedule.getTemplateType() != null ? schedule.getTemplateType().name() : null,
                schedule.getReportDays(),
                schedule.getCommentary(),
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

    private PublicClientReportResponse buildPublicReport(WorkspaceClientReportLinkEntity link,
                                                         WorkspaceSnapshot snapshot,
                                                         OffsetDateTime tokenExpiresAt) {
        AnalyticsOverviewResponse overview = withWorkspaceContext(snapshot.workspace().getId(),
                () -> analyticsApiService.getOverview("client-report", link.getReportDays()));
        List<PlatformStatsResponse> platformStats = withWorkspaceContext(snapshot.workspace().getId(),
                () -> analyticsApiService.getPlatformStats("client-report", link.getReportDays()));
        List<TopPostResponse> topPosts = withWorkspaceContext(snapshot.workspace().getId(),
                () -> analyticsApiService.getTopPosts("client-report", link.getReportDays(), resolveSnapshotType(link.getReportDays())));
        List<TimelinePointResponse> timeline = withWorkspaceContext(snapshot.workspace().getId(),
                () -> analyticsApiService.getTimeline("client-report", link.getReportDays()));

        List<PlatformStatsResponse> sortedPlatforms = platformStats.stream()
                .sorted(Comparator.comparingLong(PlatformStatsResponse::getImpressions).reversed())
                .toList();
        List<TopPostResponse> trimmedTopPosts = topPosts.stream().limit(5).toList();
        String reportWindowLabel = "Last " + link.getReportDays() + " days";
        String commentary = normalizePublicCommentary(
                link.getCommentary(),
                link.getTemplateType(),
                overview,
                sortedPlatforms,
                reportWindowLabel
        );

        return new PublicClientReportResponse(
                link.getReportTitle(),
                defaultIfBlank(link.getClientLabel(), snapshot.workspace().getName()),
                defaultIfBlank(link.getAgencyLabel(), snapshot.companyName()),
                snapshot.workspace().getName(),
                snapshot.companyName(),
                resolveLogoUrl(snapshot),
                link.getTemplateType().name(),
                link.getReportDays(),
                reportWindowLabel,
                commentary,
                buildHighlights(link, overview, sortedPlatforms, trimmedTopPosts, reportWindowLabel),
                OffsetDateTime.now(ZoneOffset.UTC),
                tokenExpiresAt,
                overview,
                sortedPlatforms,
                trimmedTopPosts,
                timeline
        );
    }

    private void sendReportLinkEmailIfNeeded(WorkspaceClientReportLinkEntity link,
                                             WorkspaceSnapshot snapshot,
                                             OffsetDateTime generatedAt) {
        if (link.getRecipientEmail() == null || link.getRecipientEmail().isBlank()) {
            return;
        }
        sendReportLinkEmail(link, snapshot, generatedAt);
    }

    private void sendReportLinkEmail(WorkspaceClientReportLinkEntity link,
                                     WorkspaceSnapshot snapshot,
                                     OffsetDateTime generatedAt) {
        PublicClientReportResponse report = buildPublicReport(link, snapshot, link.getExpiresAt());
        String reportUrl = snapshot.baseUrl() + "/reports/" + clientReportTokenService.generateToken(link.getId(), link.getExpiresAt());
        emailService.sendClientReportEmail(
                link.getRecipientEmail(),
                link.getRecipientName(),
                link.getReportTitle(),
                defaultIfBlank(link.getClientLabel(), snapshot.workspace().getName()),
                defaultIfBlank(link.getAgencyLabel(), snapshot.companyName()),
                report.getReportWindowLabel(),
                reportUrl,
                report.getHighlights(),
                buildEmailCommentary(report.getCommentary(), generatedAt)
        );
    }

    private WorkspaceClientReportLinkEntity buildLinkEntity(WorkspaceSnapshot snapshot,
                                                            String createdByUserId,
                                                            Long scheduleId,
                                                            String reportTitle,
                                                            String clientLabel,
                                                            String agencyLabel,
                                                            String templateType,
                                                            Integer reportDays,
                                                            String commentary,
                                                            String recipientName,
                                                            String recipientEmail,
                                                            OffsetDateTime expiresAt,
                                                            OffsetDateTime now) {
        Integer normalizedReportDays = normalizeReportDays(reportDays);
        WorkspaceClientReportLinkEntity link = new WorkspaceClientReportLinkEntity();
        link.setId(java.util.UUID.randomUUID().toString());
        link.setWorkspaceId(snapshot.workspace().getId());
        link.setCreatedByUserId(createdByUserId);
        link.setScheduleId(scheduleId);
        link.setReportTitle(normalizeReportTitle(reportTitle, snapshot.workspace(), normalizedReportDays));
        link.setClientLabel(normalizeOptionalText(
                clientLabel != null ? clientLabel : snapshot.workspace().getName(),
                255
        ));
        link.setAgencyLabel(normalizeOptionalText(
                agencyLabel != null ? agencyLabel : snapshot.companyName(),
                255
        ));
        link.setTemplateType(normalizeTemplate(templateType));
        link.setReportDays(normalizedReportDays);
        link.setCommentary(normalizeOptionalText(commentary, 4000));
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
                                             AnalyticsOverviewResponse overview,
                                             List<PlatformStatsResponse> platformStats,
                                             String reportWindowLabel) {
        if (requestCommentary != null && !requestCommentary.isBlank()) {
            return requestCommentary.trim();
        }

        PlatformStatsResponse strongestPlatform = platformStats.stream()
                .max(Comparator.comparingLong(this::totalEngagements))
                .orElse(null);

        return switch (template) {
            case ENGAGEMENT_SPOTLIGHT -> strongestPlatform != null
                    ? strongestPlatform.getProvider() + " led engagement in "
                    + reportWindowLabel.toLowerCase(Locale.ENGLISH)
                    + ", with " + totalEngagements(strongestPlatform)
                    + " interactions across published posts."
                    : "This engagement report highlights the channels and posts creating the strongest audience response.";
            case GROWTH_SNAPSHOT -> "This growth snapshot focuses on follower momentum, publishing consistency, and the channels driving lift in the current reporting window.";
            case EXECUTIVE_SUMMARY -> overview != null
                    ? "This executive summary covers reach, engagement, and content output across "
                    + reportWindowLabel.toLowerCase(Locale.ENGLISH) + "."
                    : "This executive summary highlights the most important performance signals for the current reporting window.";
        };
    }

    private List<String> buildHighlights(WorkspaceClientReportLinkEntity link,
                                         AnalyticsOverviewResponse overview,
                                         List<PlatformStatsResponse> platformStats,
                                         List<TopPostResponse> topPosts,
                                         String reportWindowLabel) {
        List<String> highlights = new ArrayList<>();
        highlights.add("Delivered " + overview.getTotalImpressions() + " impressions and "
                + totalOverviewEngagements(overview) + " engagements across "
                + overview.getTotalPosts() + " posts in "
                + reportWindowLabel.toLowerCase(Locale.ENGLISH) + ".");

        platformStats.stream()
                .max(Comparator.comparingLong(this::totalEngagements))
                .ifPresent(platform -> highlights.add(
                        platform.getProvider() + " is currently the strongest channel by engagement, with "
                                + totalEngagements(platform) + " interactions and "
                                + platform.getImpressions() + " impressions."
                ));

        topPosts.stream()
                .findFirst()
                .ifPresent(post -> highlights.add(
                        "Top-performing content came from " + post.getProvider()
                                + ", reaching " + post.getImpressions() + " impressions at "
                                + String.format(Locale.ENGLISH, "%.2f", post.getEngagementRate())
                                + "% engagement."
                ));

        if (link.getTemplateType() == ClientReportTemplate.GROWTH_SNAPSHOT) {
            highlights.add("Follower growth landed at " + overview.getFollowerGrowth()
                    + " over the same window, giving a clear read on audience momentum.");
        }

        return highlights.stream().filter(Objects::nonNull).distinct().limit(3).toList();
    }

    private long totalEngagements(PlatformStatsResponse response) {
        return response.getLikes() + response.getComments() + response.getShares();
    }

    private long totalOverviewEngagements(AnalyticsOverviewResponse overview) {
        return overview.getTotalLikes() + overview.getTotalComments() + overview.getTotalShares();
    }

    private String resolveLogoUrl(WorkspaceSnapshot snapshot) {
        String fileKey = snapshot.workspace().getLogoS3Key();
        if ((fileKey == null || fileKey.isBlank()) && snapshot.company() != null) {
            fileKey = snapshot.company().getLogoS3Key();
        }
        if (fileKey == null || fileKey.isBlank()) {
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
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private String resolveSnapshotType(Integer reportDays) {
        if (reportDays == null || reportDays <= 30) {
            return "T30D";
        }
        return "T90D";
    }

    private String buildEmailCommentary(String reportCommentary, OffsetDateTime generatedAt) {
        String timestamp = generatedAt.format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm 'UTC'", Locale.ENGLISH));
        if (reportCommentary == null || reportCommentary.isBlank()) {
            return "Prepared " + timestamp + ". Open the live report to review the latest metrics and top-performing content.";
        }
        return reportCommentary + " Prepared " + timestamp + ".";
    }

    private String buildPdfFileName(PublicClientReportResponse report) {
        String candidate = defaultIfBlank(report.getReportTitle(), report.getClientLabel());
        if (candidate == null || candidate.isBlank()) {
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

    private record WorkspaceSnapshot(WorkspaceEntity workspace,
                                     CompanyEntity company,
                                     String companyName,
                                     String baseUrl) {
    }

    private record ResolvedClientReportLink(WorkspaceClientReportLinkEntity link,
                                            WorkspaceSnapshot workspaceSnapshot,
                                            OffsetDateTime tokenExpiresAt) {
    }

    public record ClientReportPdfDocument(byte[] bytes, String fileName) {
    }
}
