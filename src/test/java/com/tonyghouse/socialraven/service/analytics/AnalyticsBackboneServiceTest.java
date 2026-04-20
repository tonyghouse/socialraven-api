package com.tonyghouse.socialraven.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.AnalyticsJobStatus;
import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.constant.PostType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.entity.AnalyticsJobEntity;
import com.tonyghouse.socialraven.entity.AnalyticsProviderCoverageEntity;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.WorkspacePostAnalyticsEntity;
import com.tonyghouse.socialraven.repo.AccountAnalyticsSnapshotRepo;
import com.tonyghouse.socialraven.repo.AnalyticsJobRepo;
import com.tonyghouse.socialraven.repo.AnalyticsProviderCoverageRepo;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import com.tonyghouse.socialraven.repo.PostAnalyticsSnapshotRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.repo.WorkspacePostAnalyticsRepo;
import com.tonyghouse.socialraven.scheduler.PostRedisService;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AnalyticsBackboneServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void getShellBuildsWorkspaceScopedSummaryAndCoverage() {
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);
        OAuthInfoRepo oauthInfoRepo = Mockito.mock(OAuthInfoRepo.class);
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo = Mockito.mock(PostAnalyticsSnapshotRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        AnalyticsProviderCoverageRepo analyticsProviderCoverageRepo = Mockito.mock(AnalyticsProviderCoverageRepo.class);
        AnalyticsJobRepo analyticsJobRepo = Mockito.mock(AnalyticsJobRepo.class);
        PostRedisService postRedisService = Mockito.mock(PostRedisService.class);

        AnalyticsBackboneService service = new AnalyticsBackboneService(
                accountProfileService,
                oauthInfoRepo,
                postRepo,
                workspacePostAnalyticsRepo,
                postAnalyticsSnapshotRepo,
                accountAnalyticsSnapshotRepo,
                analyticsProviderCoverageRepo,
                analyticsJobRepo,
                postRedisService
        );

        WorkspaceContext.set("ws_1", WorkspaceRole.ADMIN);

        ConnectedAccount account = new ConnectedAccount();
        account.setProviderUserId("acct_1");
        account.setPlatform(Platform.linkedin);
        account.setUsername("Acme LinkedIn");

        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setId(11L);
        collection.setWorkspaceId("ws_1");
        collection.setDescription("Quarterly B2B campaign");

        PostEntity post = new PostEntity();
        post.setId(101L);
        post.setProvider(Provider.LINKEDIN);
        post.setProviderUserId("acct_1");
        post.setPostType(PostType.TEXT);
        post.setProviderPostId("urn:li:share:101");
        post.setScheduledTime(OffsetDateTime.now(ZoneOffset.UTC).minusDays(2));
        post.setPostCollection(collection);

        WorkspacePostAnalyticsEntity current = new WorkspacePostAnalyticsEntity();
        current.setPostId(101L);
        current.setWorkspaceId("ws_1");
        current.setProvider(Provider.LINKEDIN);
        current.setProviderUserId("acct_1");
        current.setLastCollectedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        when(accountProfileService.getAllConnectedAccounts("ws_1", false)).thenReturn(List.of(account));
        when(postRepo.findAnalyticsPostsByWorkspaceId("ws_1")).thenReturn(List.of(post));
        when(workspacePostAnalyticsRepo.findAllByWorkspaceId("ws_1")).thenReturn(List.of(current));
        when(postAnalyticsSnapshotRepo.findAllByWorkspaceId("ws_1")).thenReturn(List.of());
        when(accountAnalyticsSnapshotRepo.findAllByWorkspaceId("ws_1")).thenReturn(List.of());
        when(analyticsProviderCoverageRepo.findAllByWorkspaceIdOrderByProviderAsc("ws_1")).thenReturn(List.of());
        when(analyticsProviderCoverageRepo.findByWorkspaceIdAndProvider("ws_1", Provider.LINKEDIN))
                .thenReturn(Optional.empty());
        when(analyticsProviderCoverageRepo.save(any(AnalyticsProviderCoverageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(analyticsJobRepo.countByWorkspaceIdAndStatus("ws_1", AnalyticsJobStatus.PENDING)).thenReturn(2L);

        var response = service.getShell(30, "linkedin", "acct_1", null, "text");

        assertThat(response.getSummary().getConnectedAccountCount()).isEqualTo(1);
        assertThat(response.getSummary().getCampaignCount()).isEqualTo(1);
        assertThat(response.getSummary().getPublishedPostCount()).isEqualTo(1);
        assertThat(response.getSummary().getTrackedPostCount()).isEqualTo(1);
        assertThat(response.getSummary().getPendingJobCount()).isEqualTo(2);
        assertThat(response.getSummary().isHasLiveData()).isTrue();
        assertThat(response.getCoverage()).hasSize(1);
        assertThat(response.getCoverage().get(0).getProvider()).isEqualTo("LINKEDIN");
        assertThat(response.getCoverage().get(0).getPostAnalyticsState()).isEqualTo("LIVE");
        assertThat(response.getCoverage().get(0).getFreshnessStatus()).isEqualTo("FRESH");
        assertThat(response.getFilters().getPlatforms()).extracting("value").containsExactly("linkedin");
    }

    @Test
    void requestManualRefreshQueuesProviderReconcileJobs() {
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);
        OAuthInfoRepo oauthInfoRepo = Mockito.mock(OAuthInfoRepo.class);
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo = Mockito.mock(PostAnalyticsSnapshotRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        AnalyticsProviderCoverageRepo analyticsProviderCoverageRepo = Mockito.mock(AnalyticsProviderCoverageRepo.class);
        AnalyticsJobRepo analyticsJobRepo = Mockito.mock(AnalyticsJobRepo.class);
        PostRedisService postRedisService = Mockito.mock(PostRedisService.class);

        AnalyticsBackboneService service = new AnalyticsBackboneService(
                accountProfileService,
                oauthInfoRepo,
                postRepo,
                workspacePostAnalyticsRepo,
                postAnalyticsSnapshotRepo,
                accountAnalyticsSnapshotRepo,
                analyticsProviderCoverageRepo,
                analyticsJobRepo,
                postRedisService
        );

        WorkspaceContext.set("ws_1", WorkspaceRole.ADMIN);

        ConnectedAccount account = new ConnectedAccount();
        account.setProviderUserId("acct_1");
        account.setPlatform(Platform.linkedin);
        account.setUsername("Acme LinkedIn");

        AnalyticsProviderCoverageEntity coverage = new AnalyticsProviderCoverageEntity();
        coverage.setWorkspaceId("ws_1");
        coverage.setProvider(Provider.LINKEDIN);

        when(accountProfileService.getAllConnectedAccounts("ws_1", false)).thenReturn(List.of(account));
        when(analyticsProviderCoverageRepo.findByWorkspaceIdAndProvider("ws_1", Provider.LINKEDIN))
                .thenReturn(Optional.of(coverage));
        when(analyticsProviderCoverageRepo.save(any(AnalyticsProviderCoverageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(analyticsJobRepo.findByDedupeKey(any())).thenReturn(Optional.empty());
        when(analyticsJobRepo.save(any(AnalyticsJobEntity.class))).thenAnswer(invocation -> {
            AnalyticsJobEntity entity = invocation.getArgument(0);
            entity.setId(55L);
            return entity;
        });

        var response = service.requestManualRefresh("linkedin", "acct_1");

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("SCHEDULED");
        assertThat(response.getResults().get(0).getScheduledJobs()).isEqualTo(1);

        ArgumentCaptor<Map<String, Double>> redisCaptor = ArgumentCaptor.forClass(Map.class);
        verify(postRedisService).addIds(eq("analytics-snapshot-pool"), redisCaptor.capture());
        assertThat(redisCaptor.getValue()).containsKey("55");
        verify(analyticsJobRepo, never()).countByWorkspaceIdAndStatus(any(), any());
    }

    @Test
    void scheduleNightlyProviderReconcilesIncludesScheduledThreadsProviders() {
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);
        OAuthInfoRepo oauthInfoRepo = Mockito.mock(OAuthInfoRepo.class);
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo = Mockito.mock(PostAnalyticsSnapshotRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        AnalyticsProviderCoverageRepo analyticsProviderCoverageRepo = Mockito.mock(AnalyticsProviderCoverageRepo.class);
        AnalyticsJobRepo analyticsJobRepo = Mockito.mock(AnalyticsJobRepo.class);
        PostRedisService postRedisService = Mockito.mock(PostRedisService.class);

        AnalyticsBackboneService service = new AnalyticsBackboneService(
                accountProfileService,
                oauthInfoRepo,
                postRepo,
                workspacePostAnalyticsRepo,
                postAnalyticsSnapshotRepo,
                accountAnalyticsSnapshotRepo,
                analyticsProviderCoverageRepo,
                analyticsJobRepo,
                postRedisService
        );

        OAuthInfoEntity oauthInfo = new OAuthInfoEntity();
        oauthInfo.setWorkspaceId("ws_1");
        oauthInfo.setProvider(Provider.THREADS);
        oauthInfo.setProviderUserId("threads_user_1");

        when(oauthInfoRepo.findAll()).thenReturn(List.of(oauthInfo));
        when(analyticsJobRepo.findByDedupeKey(any())).thenReturn(Optional.empty());
        when(analyticsJobRepo.save(any(AnalyticsJobEntity.class))).thenAnswer(invocation -> {
            AnalyticsJobEntity entity = invocation.getArgument(0);
            entity.setId(77L);
            return entity;
        });

        service.scheduleNightlyProviderReconciles();

        ArgumentCaptor<Map<String, Double>> redisCaptor = ArgumentCaptor.forClass(Map.class);
        verify(postRedisService).addIds(eq("analytics-snapshot-pool"), redisCaptor.capture());
        assertThat(redisCaptor.getValue()).containsKey("77");
    }

    @Test
    void scheduleDailyAccountSyncsIncludesLiveTikTokProviders() {
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);
        OAuthInfoRepo oauthInfoRepo = Mockito.mock(OAuthInfoRepo.class);
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo = Mockito.mock(PostAnalyticsSnapshotRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        AnalyticsProviderCoverageRepo analyticsProviderCoverageRepo = Mockito.mock(AnalyticsProviderCoverageRepo.class);
        AnalyticsJobRepo analyticsJobRepo = Mockito.mock(AnalyticsJobRepo.class);
        PostRedisService postRedisService = Mockito.mock(PostRedisService.class);

        AnalyticsBackboneService service = new AnalyticsBackboneService(
                accountProfileService,
                oauthInfoRepo,
                postRepo,
                workspacePostAnalyticsRepo,
                postAnalyticsSnapshotRepo,
                accountAnalyticsSnapshotRepo,
                analyticsProviderCoverageRepo,
                analyticsJobRepo,
                postRedisService
        );

        OAuthInfoEntity oauthInfo = new OAuthInfoEntity();
        oauthInfo.setWorkspaceId("ws_1");
        oauthInfo.setProvider(Provider.TIKTOK);
        oauthInfo.setProviderUserId("tt_user_1");

        when(oauthInfoRepo.findAll()).thenReturn(List.of(oauthInfo));
        when(analyticsJobRepo.findByDedupeKey(any())).thenReturn(Optional.empty());
        when(analyticsJobRepo.save(any(AnalyticsJobEntity.class))).thenAnswer(invocation -> {
            AnalyticsJobEntity entity = invocation.getArgument(0);
            entity.setId(88L);
            return entity;
        });

        service.scheduleDailyAccountSyncs();

        ArgumentCaptor<Map<String, Double>> redisCaptor = ArgumentCaptor.forClass(Map.class);
        verify(postRedisService).addIds(eq("analytics-snapshot-pool"), redisCaptor.capture());
        assertThat(redisCaptor.getValue()).containsKey("88");
    }
}
