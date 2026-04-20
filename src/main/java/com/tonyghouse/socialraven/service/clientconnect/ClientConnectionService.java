package com.tonyghouse.socialraven.service.clientconnect;

import com.tonyghouse.socialraven.constant.ClientConnectionEventType;
import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.clientconnect.ClientConnectionActivityResponse;
import com.tonyghouse.socialraven.dto.clientconnect.ClientConnectionSessionResponse;
import com.tonyghouse.socialraven.dto.clientconnect.CreateClientConnectionSessionRequest;
import com.tonyghouse.socialraven.dto.clientconnect.PublicClientConnectionCallbackRequest;
import com.tonyghouse.socialraven.dto.clientconnect.PublicClientConnectionSessionResponse;
import com.tonyghouse.socialraven.entity.CompanyEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientConnectionAuditEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientConnectionSessionEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.mapper.ProviderPlatformMapper;
import com.tonyghouse.socialraven.repo.CompanyRepo;
import com.tonyghouse.socialraven.repo.WorkspaceClientConnectionAuditRepo;
import com.tonyghouse.socialraven.repo.WorkspaceClientConnectionSessionRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.service.ConnectionFailureAlertService;
import com.tonyghouse.socialraven.service.clientconnect.OAuthConnectionPersistenceService.PersistedConnection;
import com.tonyghouse.socialraven.service.provider.FacebookOAuthService;
import com.tonyghouse.socialraven.service.provider.InstagramOAuthService;
import com.tonyghouse.socialraven.service.provider.LinkedInOAuthService;
import com.tonyghouse.socialraven.service.provider.TikTokOAuthService;
import com.tonyghouse.socialraven.service.provider.ThreadsOAuthService;
import com.tonyghouse.socialraven.service.provider.XOAuthService;
import com.tonyghouse.socialraven.service.provider.YouTubeOAuthService;
import com.tonyghouse.socialraven.service.storage.StorageService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientConnectionService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<Platform> SUPPORTED_HANDOFF_PLATFORMS = List.of(
            Platform.x,
            Platform.linkedin,
            Platform.youtube,
            Platform.instagram,
            Platform.facebook,
            Platform.threads,
            Platform.tiktok
    );
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_EXPIRY_DAYS = 30;
    private static final int DEFAULT_EXPIRY_DAYS = 7;

    @Autowired
    private WorkspaceClientConnectionSessionRepo workspaceClientConnectionSessionRepo;

    @Autowired
    private WorkspaceClientConnectionAuditRepo workspaceClientConnectionAuditRepo;

    @Autowired
    private ClientConnectionTokenService clientConnectionTokenService;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private CompanyRepo companyRepo;

    @Autowired
    private StorageService storageService;

    @Autowired
    private ClerkUserService clerkUserService;

    @Autowired
    private LinkedInOAuthService linkedInOAuthService;

    @Autowired
    private YouTubeOAuthService youTubeOAuthService;

    @Autowired
    private InstagramOAuthService instagramOAuthService;

    @Autowired
    private FacebookOAuthService facebookOAuthService;

    @Autowired
    private ThreadsOAuthService threadsOAuthService;

    @Autowired
    private XOAuthService xOAuthService;

    @Autowired
    private TikTokOAuthService tikTokOAuthService;

    @Autowired
    private ConnectionFailureAlertService connectionFailureAlertService;

    @Transactional(readOnly = true)
    public List<ClientConnectionSessionResponse> getSessions(String userId) {
        String workspaceId = requireWorkspaceId();
        List<WorkspaceClientConnectionSessionEntity> sessions =
                workspaceClientConnectionSessionRepo.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        Map<String, List<WorkspaceClientConnectionAuditEntity>> activityBySessionId = loadActivityBySessionId(
                sessions.stream().map(WorkspaceClientConnectionSessionEntity::getId).toList()
        );

        return sessions.stream()
                .map(session -> toSessionResponse(session, activityBySessionId.getOrDefault(session.getId(), List.of())))
                .toList();
    }

    @Transactional
    public ClientConnectionSessionResponse createSession(String userId,
                                                         CreateClientConnectionSessionRequest request) {
        String workspaceId = requireWorkspaceId();
        WorkspaceSnapshot snapshot = loadWorkspaceSnapshot(workspaceId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        WorkspaceClientConnectionSessionEntity session = new WorkspaceClientConnectionSessionEntity();
        session.setId(UUID.randomUUID().toString());
        session.setWorkspaceId(workspaceId);
        session.setCreatedByUserId(userId);
        session.setRecipientName(normalizeOptionalText(request != null ? request.getRecipientName() : null, 255));
        session.setRecipientEmail(normalizeEmail(request != null ? request.getRecipientEmail() : null, true));
        session.setClientLabel(defaultIfBlank(
                normalizeOptionalText(request != null ? request.getClientLabel() : null, 255),
                snapshot.workspace().getName()
        ));
        session.setAgencyLabel(defaultIfBlank(
                normalizeOptionalText(request != null ? request.getAgencyLabel() : null, 255),
                snapshot.companyName()
        ));
        session.setMessage(normalizeOptionalText(request != null ? request.getMessage() : null, MAX_MESSAGE_LENGTH));
        session.setAllowedPlatforms(normalizeAllowedPlatforms(request != null ? request.getAllowedPlatforms() : null));
        session.setExpiresAt(normalizeExpiry(request != null ? request.getExpiresAt() : null, now));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);

        WorkspaceClientConnectionSessionEntity saved = workspaceClientConnectionSessionRepo.save(session);
        return toSessionResponse(saved, List.of());
    }

    @Transactional
    public void revokeSession(String userId, String sessionId) {
        String workspaceId = requireWorkspaceId();
        WorkspaceClientConnectionSessionEntity session =
                workspaceClientConnectionSessionRepo.findByIdAndWorkspaceId(sessionId, workspaceId)
                        .orElseThrow(() -> new SocialRavenException("Client connection session not found", HttpStatus.NOT_FOUND));
        if (session.getRevokedAt() != null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        session.setRevokedAt(now);
        session.setRevokedByUserId(userId);
        session.setUpdatedAt(now);
        workspaceClientConnectionSessionRepo.save(session);
    }

    @Transactional
    public PublicClientConnectionSessionResponse getPublicSession(String token) {
        ResolvedClientConnectionSession resolved = resolvePublicSession(token);
        if (!resolved.linkExpired() && !resolved.linkRevoked()) {
            markLastAccessed(resolved.session());
        }

        List<WorkspaceClientConnectionAuditEntity> activity =
                workspaceClientConnectionAuditRepo.findAllBySessionIdOrderByCreatedAtDesc(resolved.session().getId());
        return toPublicResponse(resolved, activity);
    }

    @Transactional
    public ClientConnectionActivityResponse completePublicConnection(String token,
                                                                     Platform platform,
                                                                     PublicClientConnectionCallbackRequest request) {
        ResolvedClientConnectionSession resolved = requireActivePublicSession(token);
        String actorDisplayNameInput = request != null ? request.getActorDisplayName() : null;
        String actorEmailInput = request != null ? request.getActorEmail() : null;

        try {
            Platform requestedPlatform = requireAllowedPlatform(resolved.session(), platform);
            String actorDisplayName = normalizeRequiredName(actorDisplayNameInput);
            String actorEmail = normalizeEmail(actorEmailInput, true);
            enforceRecipientEmail(resolved.session(), actorEmail);

            PersistedConnection persisted = switch (requestedPlatform) {
                case linkedin -> linkedInOAuthService.exchangeCodeForClientConnection(
                        requireCode(request),
                        resolved.session(),
                        actorDisplayName,
                        actorEmail
                );
                case youtube -> youTubeOAuthService.exchangeCodeForClientConnection(
                        requireCode(request),
                        resolved.session(),
                        actorDisplayName,
                        actorEmail
                );
                case instagram -> instagramOAuthService.exchangeCodeForClientConnection(
                        requireCode(request),
                        resolved.session(),
                        actorDisplayName,
                        actorEmail
                );
                case facebook -> facebookOAuthService.exchangeCodeForClientConnection(
                        requireCode(request),
                        resolved.session(),
                        actorDisplayName,
                        actorEmail
                );
                case threads -> threadsOAuthService.exchangeCodeForClientConnection(
                        requireCode(request),
                        resolved.session(),
                        actorDisplayName,
                        actorEmail
                );
                case tiktok -> tikTokOAuthService.exchangeCodeForClientConnection(
                        requireCode(request),
                        resolved.session(),
                        actorDisplayName,
                        actorEmail
                );
                case x -> xOAuthService.completeClientConnection(
                        request != null ? request.getAccessToken() : null,
                        request != null ? request.getAccessTokenSecret() : null,
                        resolved.session(),
                        actorDisplayName,
                        actorEmail
                );
                default -> throw new SocialRavenException(
                        "This platform is not supported for client connection handoff",
                        HttpStatus.BAD_REQUEST
                );
            };

            markLastAccessed(resolved.session());
            WorkspaceClientConnectionAuditEntity audit = recordConnectionEvent(
                    resolved.session(),
                    persisted.entity().getProvider(),
                    persisted.entity().getProviderUserId(),
                    actorDisplayName,
                    actorEmail,
                    persisted.reauthorized()
            );
            return toActivityResponse(audit);
        } catch (Exception exception) {
            connectionFailureAlertService.notifyClientConnectionFailure(
                    platform,
                    resolved.session(),
                    actorDisplayNameInput,
                    actorEmailInput,
                    exception
            );
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(exception);
        }
    }

    private String requireWorkspaceId() {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new SocialRavenException("Workspace context is required", HttpStatus.BAD_REQUEST);
        }
        return workspaceId;
    }

    private String requireCode(PublicClientConnectionCallbackRequest request) {
        String code = request != null ? request.getCode() : null;
        if (code == null || code.isBlank()) {
            throw new SocialRavenException("OAuth code is required", HttpStatus.BAD_REQUEST);
        }
        return code;
    }

    private Platform requireAllowedPlatform(WorkspaceClientConnectionSessionEntity session, Platform platform) {
        if (platform == null || !SUPPORTED_HANDOFF_PLATFORMS.contains(platform)) {
            throw new SocialRavenException(
                    "This platform is not supported for client connection handoff",
                    HttpStatus.BAD_REQUEST
            );
        }
        List<Platform> allowedPlatforms = session.getAllowedPlatforms() != null ? session.getAllowedPlatforms() : List.of();
        if (!allowedPlatforms.contains(platform)) {
            throw new SocialRavenException("This platform is not enabled for the handoff", HttpStatus.FORBIDDEN);
        }
        return platform;
    }

    private void enforceRecipientEmail(WorkspaceClientConnectionSessionEntity session, String actorEmail) {
        String recipientEmail = session.getRecipientEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }
        if (!recipientEmail.equalsIgnoreCase(actorEmail)) {
            throw new SocialRavenException(
                    "This handoff is restricted to the invited client contact email",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private Map<String, List<WorkspaceClientConnectionAuditEntity>> loadActivityBySessionId(Collection<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }

        return workspaceClientConnectionAuditRepo.findAllBySessionIdInOrderByCreatedAtDesc(sessionIds).stream()
                .collect(Collectors.groupingBy(
                        WorkspaceClientConnectionAuditEntity::getSessionId,
                        LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)
                ));
    }

    private ClientConnectionSessionResponse toSessionResponse(WorkspaceClientConnectionSessionEntity session,
                                                              List<WorkspaceClientConnectionAuditEntity> activity) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean active = session.getRevokedAt() == null
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(now);

        return new ClientConnectionSessionResponse(
                session.getId(),
                clientConnectionTokenService.generateToken(session.getId(), session.getExpiresAt()),
                session.getCreatedByUserId(),
                resolveDisplayName(session.getCreatedByUserId()),
                session.getRecipientName(),
                session.getRecipientEmail(),
                session.getClientLabel(),
                session.getAgencyLabel(),
                session.getMessage(),
                toPlatformNames(session.getAllowedPlatforms()),
                session.getExpiresAt(),
                session.getRevokedAt(),
                session.getLastAccessedAt(),
                activity.isEmpty() ? null : activity.get(0).getCreatedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                active,
                activity.size(),
                activity.stream().limit(5).map(this::toActivityResponse).toList()
        );
    }

    private PublicClientConnectionSessionResponse toPublicResponse(ResolvedClientConnectionSession resolved,
                                                                  List<WorkspaceClientConnectionAuditEntity> activity) {
        WorkspaceSnapshot snapshot = resolved.workspaceSnapshot();
        return new PublicClientConnectionSessionResponse(
                defaultIfBlank(resolved.session().getClientLabel(), snapshot.workspace().getName()),
                defaultIfBlank(resolved.session().getAgencyLabel(), snapshot.companyName()),
                snapshot.workspace().getName(),
                snapshot.companyName(),
                resolveLogoUrl(snapshot),
                resolved.session().getRecipientName(),
                maskEmail(resolved.session().getRecipientEmail()),
                defaultIfBlank(
                        resolved.session().getMessage(),
                        "Use this secure handoff to connect the social accounts your agency will manage inside this workspace."
                ),
                toPlatformNames(resolved.session().getAllowedPlatforms()),
                resolved.session().getExpiresAt(),
                resolved.linkRevoked(),
                resolved.linkExpired(),
                !resolved.linkExpired() && !resolved.linkRevoked(),
                activity.stream().limit(5).map(this::toActivityResponse).toList()
        );
    }

    private WorkspaceClientConnectionAuditEntity recordConnectionEvent(WorkspaceClientConnectionSessionEntity session,
                                                                       Provider provider,
                                                                       String providerUserId,
                                                                       String actorDisplayName,
                                                                       String actorEmail,
                                                                       boolean reauthorized) {
        WorkspaceClientConnectionAuditEntity audit = new WorkspaceClientConnectionAuditEntity();
        audit.setSessionId(session.getId());
        audit.setWorkspaceId(session.getWorkspaceId());
        audit.setProvider(provider);
        audit.setProviderUserId(providerUserId);
        audit.setEventType(reauthorized ? ClientConnectionEventType.RECONNECTED : ClientConnectionEventType.CONNECTED);
        audit.setActorDisplayName(actorDisplayName);
        audit.setActorEmail(actorEmail);
        audit.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return workspaceClientConnectionAuditRepo.save(audit);
    }

    private ClientConnectionActivityResponse toActivityResponse(WorkspaceClientConnectionAuditEntity entity) {
        Platform platform = ProviderPlatformMapper.getPlatformByProvider(entity.getProvider());
        return new ClientConnectionActivityResponse(
                platform != null ? platform.name() : entity.getProvider().name().toLowerCase(Locale.ENGLISH),
                entity.getProviderUserId(),
                entity.getEventType().name(),
                entity.getActorDisplayName(),
                entity.getActorEmail(),
                entity.getCreatedAt()
        );
    }

    private ResolvedClientConnectionSession resolvePublicSession(String token) {
        ClientConnectionTokenService.ValidatedClientConnectionToken validated =
                clientConnectionTokenService.parseAndValidate(token);
        WorkspaceClientConnectionSessionEntity session = workspaceClientConnectionSessionRepo.findById(validated.sessionId())
                .orElseThrow(() -> new SocialRavenException("Client connection session not found", HttpStatus.NOT_FOUND));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean linkRevoked = session.getRevokedAt() != null;
        boolean linkExpired = !validated.expiresAt().isAfter(now)
                || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(now);

        return new ResolvedClientConnectionSession(
                session,
                loadWorkspaceSnapshot(session.getWorkspaceId()),
                validated.expiresAt(),
                linkRevoked,
                linkExpired
        );
    }

    private ResolvedClientConnectionSession requireActivePublicSession(String token) {
        ResolvedClientConnectionSession resolved = resolvePublicSession(token);
        if (resolved.linkRevoked()) {
            throw new SocialRavenException("This client connection link has been revoked", HttpStatus.GONE);
        }
        if (resolved.linkExpired()) {
            throw new SocialRavenException("This client connection link has expired", HttpStatus.GONE);
        }
        return resolved;
    }

    private WorkspaceSnapshot loadWorkspaceSnapshot(String workspaceId) {
        WorkspaceEntity workspace = workspaceRepo.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));
        CompanyEntity company = companyRepo.findById(workspace.getCompanyId()).orElse(null);
        return new WorkspaceSnapshot(
                workspace,
                company != null && company.getName() != null ? company.getName() : workspace.getName(),
                company,
                workspace.getLogoS3Key()
        );
    }

    private void markLastAccessed(WorkspaceClientConnectionSessionEntity session) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        session.setLastAccessedAt(now);
        session.setUpdatedAt(now);
        workspaceClientConnectionSessionRepo.save(session);
    }

    private List<String> toPlatformNames(List<Platform> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return List.of();
        }
        return platforms.stream().map(Enum::name).toList();
    }

    private List<Platform> normalizeAllowedPlatforms(List<String> requestedPlatforms) {
        if (requestedPlatforms == null || requestedPlatforms.isEmpty()) {
            return SUPPORTED_HANDOFF_PLATFORMS;
        }

        Set<Platform> normalized = new LinkedHashSet<>();
        for (String requested : requestedPlatforms) {
            if (requested == null || requested.isBlank()) {
                continue;
            }
            try {
                Platform platform = Platform.valueOf(requested.trim().toLowerCase(Locale.ENGLISH));
                if (!SUPPORTED_HANDOFF_PLATFORMS.contains(platform)) {
                    throw new SocialRavenException(
                            "Unsupported client handoff platform: " + requested,
                            HttpStatus.BAD_REQUEST
                    );
                }
                normalized.add(platform);
            } catch (IllegalArgumentException ex) {
                throw new SocialRavenException(
                        "Unsupported client handoff platform: " + requested,
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        if (normalized.isEmpty()) {
            return SUPPORTED_HANDOFF_PLATFORMS;
        }

        return SUPPORTED_HANDOFF_PLATFORMS.stream()
                .filter(normalized::contains)
                .toList();
    }

    private OffsetDateTime normalizeExpiry(OffsetDateTime requestedExpiry, OffsetDateTime now) {
        OffsetDateTime expiresAt = requestedExpiry != null ? requestedExpiry : now.plusDays(DEFAULT_EXPIRY_DAYS);
        if (!expiresAt.isAfter(now)) {
            throw new SocialRavenException("expiresAt must be in the future", HttpStatus.BAD_REQUEST);
        }
        if (expiresAt.isAfter(now.plusDays(MAX_EXPIRY_DAYS))) {
            throw new SocialRavenException(
                    "Client connection links cannot be active for more than " + MAX_EXPIRY_DAYS + " days",
                    HttpStatus.BAD_REQUEST
            );
        }
        return expiresAt;
    }

    private String normalizeRequiredName(String value) {
        String normalized = normalizeOptionalText(value, 255);
        if (normalized == null) {
            throw new SocialRavenException("Client contact name is required", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeEmail(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new SocialRavenException("Client contact email is required", HttpStatus.BAD_REQUEST);
            }
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ENGLISH);
        if (normalized.length() > 320 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new SocialRavenException("Recipient email is invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new SocialRavenException("Input exceeds maximum length", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String resolveDisplayName(String userId) {
        ClerkUserService.UserProfile profile = clerkUserService.getUserProfile(userId);
        if (profile == null) {
            return userId;
        }

        String first = profile.firstName() != null ? profile.firstName().trim() : "";
        String last = profile.lastName() != null ? profile.lastName().trim() : "";
        String fullName = (first + " " + last).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        if (profile.email() != null && !profile.email().isBlank()) {
            return profile.email();
        }
        return userId;
    }

    private String resolveLogoUrl(WorkspaceSnapshot snapshot) {
        String logoKey = null;
        if (snapshot.company() != null && snapshot.company().getLogoS3Key() != null && !snapshot.company().getLogoS3Key().isBlank()) {
            logoKey = snapshot.company().getLogoS3Key();
        } else if (snapshot.workspaceLogoS3Key() != null && !snapshot.workspaceLogoS3Key().isBlank()) {
            logoKey = snapshot.workspaceLogoS3Key();
        }

        if (logoKey == null) {
            return null;
        }

        return storageService.generatePresignedGetUrl(logoKey, Duration.ofHours(24));
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String maskEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String[] parts = value.split("@", 2);
        if (parts.length != 2) {
            return value;
        }

        String local = parts[0];
        String domain = parts[1];
        String visible = local.substring(0, Math.min(2, local.length()));
        String hidden = "*".repeat(Math.max(local.length() - visible.length(), 1));
        return visible + hidden + "@" + domain;
    }

    private record WorkspaceSnapshot(WorkspaceEntity workspace,
                                     String companyName,
                                     CompanyEntity company,
                                     String workspaceLogoS3Key) {
    }

    private record ResolvedClientConnectionSession(WorkspaceClientConnectionSessionEntity session,
                                                   WorkspaceSnapshot workspaceSnapshot,
                                                   OffsetDateTime tokenExpiresAt,
                                                   boolean linkRevoked,
                                                   boolean linkExpired) {
    }
}
