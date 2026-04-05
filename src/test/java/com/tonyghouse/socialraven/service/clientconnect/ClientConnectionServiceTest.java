package com.tonyghouse.socialraven.service.clientconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.clientconnect.CreateClientConnectionSessionRequest;
import com.tonyghouse.socialraven.dto.clientconnect.PublicClientConnectionCallbackRequest;
import com.tonyghouse.socialraven.entity.CompanyEntity;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientConnectionAuditEntity;
import com.tonyghouse.socialraven.entity.WorkspaceClientConnectionSessionEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.CompanyRepo;
import com.tonyghouse.socialraven.repo.WorkspaceClientConnectionAuditRepo;
import com.tonyghouse.socialraven.repo.WorkspaceClientConnectionSessionRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.service.clientconnect.OAuthConnectionPersistenceService.PersistedConnection;
import com.tonyghouse.socialraven.service.provider.FacebookOAuthService;
import com.tonyghouse.socialraven.service.provider.InstagramOAuthService;
import com.tonyghouse.socialraven.service.provider.LinkedInOAuthService;
import com.tonyghouse.socialraven.service.provider.XOAuthService;
import com.tonyghouse.socialraven.service.provider.YouTubeOAuthService;
import com.tonyghouse.socialraven.service.storage.StorageService;
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

class ClientConnectionServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void createSessionDefaultsBrandingAndSupportedPlatforms() {
        WorkspaceClientConnectionSessionRepo sessionRepo = mock(WorkspaceClientConnectionSessionRepo.class);
        WorkspaceClientConnectionAuditRepo auditRepo = mock(WorkspaceClientConnectionAuditRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);
        StorageService storageService = mock(StorageService.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);

        ClientConnectionService service = createService(
                sessionRepo,
                auditRepo,
                workspaceRepo,
                companyRepo,
                storageService,
                clerkUserService
        );

        WorkspaceContext.set("workspace_1", WorkspaceRole.ADMIN);

        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1"))
                .thenReturn(Optional.of(workspace("workspace_1", "company_1", "Orbit Foods")));
        when(companyRepo.findById("company_1"))
                .thenReturn(Optional.of(company("company_1", "Northshore Agency")));
        when(clerkUserService.getUserProfile("user_1"))
                .thenReturn(new ClerkUserService.UserProfile("Ava", "Manager", "ava@agency.com"));
        when(sessionRepo.save(any(WorkspaceClientConnectionSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateClientConnectionSessionRequest request = new CreateClientConnectionSessionRequest();
        request.setRecipientEmail("client@orbitfoods.com");

        var response = service.createSession("user_1", request);

        ArgumentCaptor<WorkspaceClientConnectionSessionEntity> captor =
                ArgumentCaptor.forClass(WorkspaceClientConnectionSessionEntity.class);
        verify(sessionRepo).save(captor.capture());
        WorkspaceClientConnectionSessionEntity saved = captor.getValue();

        assertThat(saved.getRecipientEmail()).isEqualTo("client@orbitfoods.com");
        assertThat(saved.getClientLabel()).isEqualTo("Orbit Foods");
        assertThat(saved.getAgencyLabel()).isEqualTo("Northshore Agency");
        assertThat(saved.getAllowedPlatforms())
                .containsExactly(Platform.x, Platform.linkedin, Platform.youtube, Platform.instagram, Platform.facebook);
        assertThat(response.getCreatedByDisplayName()).isEqualTo("Ava Manager");
        assertThat(response.getAllowedPlatforms())
                .containsExactly("x", "linkedin", "youtube", "instagram", "facebook");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void getPublicSessionBuildsBrandingAndActivity() {
        WorkspaceClientConnectionSessionRepo sessionRepo = mock(WorkspaceClientConnectionSessionRepo.class);
        WorkspaceClientConnectionAuditRepo auditRepo = mock(WorkspaceClientConnectionAuditRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);
        StorageService storageService = mock(StorageService.class);

        ClientConnectionService service = createService(
                sessionRepo,
                auditRepo,
                workspaceRepo,
                companyRepo,
                storageService,
                mock(ClerkUserService.class)
        );

        WorkspaceClientConnectionSessionEntity session = session("session_1", "workspace_1");
        session.setRecipientEmail("client@orbitfoods.com");
        session.setMessage("Connect the channels your agency will manage.");
        session.setAllowedPlatforms(List.of(Platform.linkedin, Platform.facebook));

        WorkspaceClientConnectionAuditEntity audit = new WorkspaceClientConnectionAuditEntity();
        audit.setSessionId("session_1");
        audit.setProvider(Provider.LINKEDIN);
        audit.setProviderUserId("linkedin_123");
        audit.setEventType(com.tonyghouse.socialraven.constant.ClientConnectionEventType.CONNECTED);
        audit.setActorDisplayName("Ava Client");
        audit.setActorEmail("client@orbitfoods.com");
        audit.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        when(sessionRepo.findById("session_1")).thenReturn(Optional.of(session));
        when(sessionRepo.save(any(WorkspaceClientConnectionSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(auditRepo.findAllBySessionIdOrderByCreatedAtDesc("session_1")).thenReturn(List.of(audit));
        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1"))
                .thenReturn(Optional.of(workspace("workspace_1", "company_1", "Orbit Foods")));
        when(companyRepo.findById("company_1"))
                .thenReturn(Optional.of(company("company_1", "Northshore Agency")));
        when(storageService.generatePresignedGetUrl(eq("logos/agency.png"), any(Duration.class)))
                .thenReturn("https://cdn.socialraven.test/agency.png");

        String token = tokenService().generateToken("session_1", session.getExpiresAt());
        var response = service.getPublicSession(token);

        assertThat(response.getAgencyLabel()).isEqualTo("Northshore Agency");
        assertThat(response.getLogoUrl()).isEqualTo("https://cdn.socialraven.test/agency.png");
        assertThat(response.getAllowedPlatforms()).containsExactly("linkedin", "facebook");
        assertThat(response.isCanConnect()).isTrue();
        assertThat(response.getRecentActivity()).singleElement().satisfies(item -> {
            assertThat(item.getPlatform()).isEqualTo("linkedin");
            assertThat(item.getEventType()).isEqualTo("CONNECTED");
        });
    }

    @Test
    void completePublicConnectionRecordsReconnectAudit() {
        WorkspaceClientConnectionSessionRepo sessionRepo = mock(WorkspaceClientConnectionSessionRepo.class);
        WorkspaceClientConnectionAuditRepo auditRepo = mock(WorkspaceClientConnectionAuditRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);
        LinkedInOAuthService linkedInOAuthService = mock(LinkedInOAuthService.class);

        ClientConnectionService service = createService(
                sessionRepo,
                auditRepo,
                workspaceRepo,
                companyRepo,
                mock(StorageService.class),
                mock(ClerkUserService.class),
                linkedInOAuthService,
                mock(YouTubeOAuthService.class),
                mock(InstagramOAuthService.class),
                mock(FacebookOAuthService.class),
                mock(XOAuthService.class)
        );

        WorkspaceClientConnectionSessionEntity session = session("session_1", "workspace_1");
        session.setAllowedPlatforms(List.of(Platform.linkedin));
        session.setRecipientEmail("client@orbitfoods.com");

        OAuthInfoEntity oauthInfo = new OAuthInfoEntity();
        oauthInfo.setProvider(Provider.LINKEDIN);
        oauthInfo.setProviderUserId("linkedin_123");

        when(sessionRepo.findById("session_1")).thenReturn(Optional.of(session));
        when(sessionRepo.save(any(WorkspaceClientConnectionSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1"))
                .thenReturn(Optional.of(workspace("workspace_1", "company_1", "Orbit Foods")));
        when(companyRepo.findById("company_1"))
                .thenReturn(Optional.of(company("company_1", "Northshore Agency")));
        when(linkedInOAuthService.exchangeCodeForClientConnection(
                eq("oauth-code"),
                eq(session),
                eq("Ava Client"),
                eq("client@orbitfoods.com")
        )).thenReturn(new PersistedConnection(oauthInfo, true));
        when(auditRepo.save(any(WorkspaceClientConnectionAuditEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PublicClientConnectionCallbackRequest request = new PublicClientConnectionCallbackRequest();
        request.setCode("oauth-code");
        request.setActorDisplayName("Ava Client");
        request.setActorEmail("client@orbitfoods.com");

        String token = tokenService().generateToken("session_1", session.getExpiresAt());
        var response = service.completePublicConnection(token, Platform.linkedin, request);

        assertThat(response.getPlatform()).isEqualTo("linkedin");
        assertThat(response.getEventType()).isEqualTo("RECONNECTED");
        assertThat(response.getActorEmail()).isEqualTo("client@orbitfoods.com");
    }

    @Test
    void completePublicConnectionRejectsMismatchedRecipientEmail() {
        WorkspaceClientConnectionSessionRepo sessionRepo = mock(WorkspaceClientConnectionSessionRepo.class);
        WorkspaceClientConnectionAuditRepo auditRepo = mock(WorkspaceClientConnectionAuditRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        CompanyRepo companyRepo = mock(CompanyRepo.class);

        ClientConnectionService service = createService(
                sessionRepo,
                auditRepo,
                workspaceRepo,
                companyRepo,
                mock(StorageService.class),
                mock(ClerkUserService.class)
        );

        WorkspaceClientConnectionSessionEntity session = session("session_1", "workspace_1");
        session.setAllowedPlatforms(List.of(Platform.linkedin));
        session.setRecipientEmail("client@orbitfoods.com");

        when(sessionRepo.findById("session_1")).thenReturn(Optional.of(session));
        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1"))
                .thenReturn(Optional.of(workspace("workspace_1", "company_1", "Orbit Foods")));
        when(companyRepo.findById("company_1"))
                .thenReturn(Optional.of(company("company_1", "Northshore Agency")));

        PublicClientConnectionCallbackRequest request = new PublicClientConnectionCallbackRequest();
        request.setCode("oauth-code");
        request.setActorDisplayName("Wrong Contact");
        request.setActorEmail("other@orbitfoods.com");

        String token = tokenService().generateToken("session_1", session.getExpiresAt());

        assertThatThrownBy(() -> service.completePublicConnection(token, Platform.linkedin, request))
                .isInstanceOf(SocialRavenException.class)
                .hasMessage("This handoff is restricted to the invited client contact email");
    }

    private ClientConnectionService createService(WorkspaceClientConnectionSessionRepo sessionRepo,
                                                  WorkspaceClientConnectionAuditRepo auditRepo,
                                                  WorkspaceRepo workspaceRepo,
                                                  CompanyRepo companyRepo,
                                                  StorageService storageService,
                                                  ClerkUserService clerkUserService) {
        return createService(
                sessionRepo,
                auditRepo,
                workspaceRepo,
                companyRepo,
                storageService,
                clerkUserService,
                mock(LinkedInOAuthService.class),
                mock(YouTubeOAuthService.class),
                mock(InstagramOAuthService.class),
                mock(FacebookOAuthService.class),
                mock(XOAuthService.class)
        );
    }

    private ClientConnectionService createService(WorkspaceClientConnectionSessionRepo sessionRepo,
                                                  WorkspaceClientConnectionAuditRepo auditRepo,
                                                  WorkspaceRepo workspaceRepo,
                                                  CompanyRepo companyRepo,
                                                  StorageService storageService,
                                                  ClerkUserService clerkUserService,
                                                  LinkedInOAuthService linkedInOAuthService,
                                                  YouTubeOAuthService youTubeOAuthService,
                                                  InstagramOAuthService instagramOAuthService,
                                                  FacebookOAuthService facebookOAuthService,
                                                  XOAuthService xOAuthService) {
        ClientConnectionService service = new ClientConnectionService();
        ReflectionTestUtils.setField(service, "workspaceClientConnectionSessionRepo", sessionRepo);
        ReflectionTestUtils.setField(service, "workspaceClientConnectionAuditRepo", auditRepo);
        ReflectionTestUtils.setField(service, "clientConnectionTokenService", tokenService());
        ReflectionTestUtils.setField(service, "workspaceRepo", workspaceRepo);
        ReflectionTestUtils.setField(service, "companyRepo", companyRepo);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        ReflectionTestUtils.setField(service, "clerkUserService", clerkUserService);
        ReflectionTestUtils.setField(service, "linkedInOAuthService", linkedInOAuthService);
        ReflectionTestUtils.setField(service, "youTubeOAuthService", youTubeOAuthService);
        ReflectionTestUtils.setField(service, "instagramOAuthService", instagramOAuthService);
        ReflectionTestUtils.setField(service, "facebookOAuthService", facebookOAuthService);
        ReflectionTestUtils.setField(service, "xOAuthService", xOAuthService);
        return service;
    }

    private ClientConnectionTokenService tokenService() {
        ClientConnectionTokenService service = new ClientConnectionTokenService();
        ReflectionTestUtils.setField(service, "clientConnectSecret", "unit-test-client-connect-secret");
        return service;
    }

    private WorkspaceClientConnectionSessionEntity session(String sessionId, String workspaceId) {
        WorkspaceClientConnectionSessionEntity entity = new WorkspaceClientConnectionSessionEntity();
        entity.setId(sessionId);
        entity.setWorkspaceId(workspaceId);
        entity.setCreatedByUserId("user_1");
        entity.setRecipientName("Ava Client");
        entity.setClientLabel("Orbit Foods");
        entity.setAgencyLabel("Northshore Agency");
        entity.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
        entity.setUpdatedAt(entity.getCreatedAt());
        return entity;
    }

    private WorkspaceEntity workspace(String workspaceId, String companyId, String name) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setId(workspaceId);
        entity.setCompanyId(companyId);
        entity.setName(name);
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
