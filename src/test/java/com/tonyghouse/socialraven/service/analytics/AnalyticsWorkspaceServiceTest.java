package com.tonyghouse.socialraven.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.constant.PostType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewMetricResponse;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.AccountAnalyticsSnapshotEntity;
import com.tonyghouse.socialraven.entity.PostMediaEntity;
import com.tonyghouse.socialraven.entity.WorkspacePostAnalyticsEntity;
import com.tonyghouse.socialraven.repo.AccountAnalyticsSnapshotRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.repo.WorkspacePostAnalyticsRepo;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import java.time.LocalDate;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AnalyticsWorkspaceServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void getOverviewBuildsPeriodOverPeriodWorkspaceMetrics() {
        AnalyticsWorkspaceService service = serviceWithFixtures();

        var response = service.getOverview(7, null, null, null, null);
        Map<String, AnalyticsOverviewMetricResponse> metrics = response.getMetrics().stream()
                .collect(Collectors.toMap(AnalyticsOverviewMetricResponse::getKey, metric -> metric));

        assertThat(response.getCurrentRangeLabel()).isEqualTo("Last 7 days");
        assertThat(response.getPreviousRangeLabel()).isEqualTo("Previous 7 days");
        assertThat(metrics.get("impressions").getCurrentValue()).isEqualTo(100.0);
        assertThat(metrics.get("impressions").getPreviousValue()).isEqualTo(40.0);
        assertThat(metrics.get("impressions").getDeltaValue()).isEqualTo(60.0);
        assertThat(metrics.get("engagements").getCurrentValue()).isEqualTo(17.0);
        assertThat(metrics.get("engagements").getPreviousValue()).isEqualTo(6.0);
        assertThat(metrics.get("clicks").getCurrentValue()).isEqualTo(8.0);
        assertThat(metrics.get("videoViews").getCurrentValue()).isEqualTo(25.0);
        assertThat(metrics.get("postsPublished").getCurrentValue()).isEqualTo(1.0);
    }

    @Test
    void postTableAndRankingsRespectWorkspaceFiltersAndSorting() {
        AnalyticsWorkspaceService service = serviceWithFixtures();

        var table = service.getPostTable(30, "linkedin", null, null, null, "impressions", "desc", 0, 1);
        assertThat(table.getTotalCount()).isEqualTo(2);
        assertThat(table.isHasNext()).isTrue();
        assertThat(table.getRows()).hasSize(1);
        assertThat(table.getRows().get(0).getPostId()).isEqualTo(101L);
        assertThat(table.getRows().get(0).getAccountName()).isEqualTo("Acme LinkedIn");
        assertThat(table.getRows().get(0).getPublishedAt()).isNotNull();

        var rankings = service.getPostRankings(30, "linkedin", null, null, null, "engagements", 2);
        assertThat(rankings.getMetric()).isEqualTo("engagements");
        assertThat(rankings.getTopPosts()).extracting("postId").containsExactly(101L, 102L);
        assertThat(rankings.getWorstPosts()).extracting("postId").containsExactly(102L, 101L);
    }

    @Test
    void linkedInPageActivityAggregatesRealOrganizationSnapshots() {
        AnalyticsWorkspaceService service = serviceWithFixtures();

        var response = service.getLinkedInPageActivity(30, "linkedin", null);

        assertThat(response.getTrackedAccounts()).isEqualTo(1L);
        assertThat(response.getTotalPageViews()).isEqualTo(190L);
        assertThat(response.getTotalUniquePageViews()).isEqualTo(145L);
        assertThat(response.getTotalClicks()).isEqualTo(17L);
        assertThat(response.getTotalFollowerDelta()).isEqualTo(30L);
        assertThat(response.getRows()).hasSize(1);
        assertThat(response.getRows().get(0).getAccountName()).isEqualTo("Acme LinkedIn");
        assertThat(response.getRows().get(0).getFollowers()).isEqualTo(230L);
        assertThat(response.getRows().get(0).getFollowerDelta()).isEqualTo(30L);
        assertThat(response.getRows().get(0).getPageViews()).isEqualTo(190L);
        assertThat(response.getRows().get(0).getClicks()).isEqualTo(17L);
        assertThat(response.getRows().get(0).getPageViewSharePercent()).isEqualTo(100.0);
        assertThat(response.getRows().get(0).getClickSharePercent()).isEqualTo(100.0);
    }

    @Test
    void youTubeChannelActivityAggregatesDailyChannelSnapshots() {
        AnalyticsWorkspaceService service = serviceWithYouTubeFixtures();

        var response = service.getYouTubeChannelActivity(30, "youtube", null);

        assertThat(response.getTrackedAccounts()).isEqualTo(1L);
        assertThat(response.getTotalVideoViews()).isEqualTo(470L);
        assertThat(response.getTotalLikes()).isEqualTo(61L);
        assertThat(response.getTotalComments()).isEqualTo(17L);
        assertThat(response.getTotalShares()).isEqualTo(9L);
        assertThat(response.getTotalWatchTimeMinutes()).isEqualTo(845L);
        assertThat(response.getTotalSubscriberDelta()).isEqualTo(13L);
        assertThat(response.getTrend()).hasSize(2);
        assertThat(response.getRows()).hasSize(1);
        assertThat(response.getRows().get(0).getAccountName()).isEqualTo("Acme YouTube");
        assertThat(response.getRows().get(0).getFollowers()).isEqualTo(1250L);
        assertThat(response.getRows().get(0).getWatchTimeMinutes()).isEqualTo(845L);
        assertThat(response.getRows().get(0).getViewSharePercent()).isEqualTo(100.0);
        assertThat(response.getRows().get(0).getWatchTimeSharePercent()).isEqualTo(100.0);
    }

    @Test
    void tikTokCreatorActivityAggregatesCreatorProfileSnapshots() {
        AnalyticsWorkspaceService service = serviceWithTikTokFixtures();

        var response = service.getTikTokCreatorActivity(30, "tiktok", null);

        assertThat(response.getTrackedAccounts()).isEqualTo(1L);
        assertThat(response.getTotalFollowers()).isEqualTo(540L);
        assertThat(response.getTotalFollowing()).isEqualTo(118L);
        assertThat(response.getTotalLikesTotal()).isEqualTo(18_600L);
        assertThat(response.getTotalVideoCount()).isEqualTo(42L);
        assertThat(response.getTotalFollowerDelta()).isEqualTo(40L);
        assertThat(response.getTotalLikesDelta()).isEqualTo(2_100L);
        assertThat(response.getTotalVideoDelta()).isEqualTo(5L);
        assertThat(response.getTrend()).hasSize(2);
        assertThat(response.getRows()).hasSize(1);
        assertThat(response.getRows().get(0).getAccountName()).isEqualTo("Acme TikTok");
        assertThat(response.getRows().get(0).getFollowers()).isEqualTo(540L);
        assertThat(response.getRows().get(0).getFollowing()).isEqualTo(118L);
        assertThat(response.getRows().get(0).getLikesTotal()).isEqualTo(18_600L);
        assertThat(response.getRows().get(0).getVideoCount()).isEqualTo(42L);
        assertThat(response.getRows().get(0).getFollowerSharePercent()).isEqualTo(100.0);
        assertThat(response.getRows().get(0).getLikesSharePercent()).isEqualTo(100.0);
    }

    @Test
    void trendExplorerBuildsDailyAndWeeklyRollupsForWorkspaceSlice() {
        TrendFixture fixture = serviceWithTrendFixtures();

        var response = fixture.service().getTrendExplorer(30, null, null, null, null, "engagements");
        var youTubeDay = response.getDaily().stream()
                .filter(point -> point.getBucketStartDate().equals(fixture.youTubeVideoAt().toLocalDate()))
                .findFirst()
                .orElseThrow();

        long expectedDailyPoints = ChronoUnit.DAYS.between(
                response.getCurrentStartAt().toLocalDate(),
                response.getCurrentEndAt().toLocalDate()
        ) + 1;

        assertThat(response.getMetric()).isEqualTo("engagements");
        assertThat(response.getMetricLabel()).isEqualTo("Engagements");
        assertThat(response.getMetricFormat()).isEqualTo("number");
        assertThat(response.getTotalPerformanceValue()).isEqualTo(140.0);
        assertThat(response.getTotalPostsPublished()).isEqualTo(4L);
        assertThat(response.getAveragePerformancePerPost()).isEqualTo(35.0);
        assertThat(response.getDaily()).hasSize((int) expectedDailyPoints);
        assertThat(youTubeDay.getPerformanceValue()).isEqualTo(60.0);
        assertThat(youTubeDay.getPostsPublished()).isEqualTo(1L);
        assertThat(response.getWeekly()).allSatisfy(point -> assertThat(point.getPostsPublished()).isGreaterThanOrEqualTo(0L));
        assertThat(response.getWeekly().stream().mapToDouble(point -> point.getPerformanceValue()).sum())
                .isEqualTo(140.0);
    }

    @Test
    void breakdownEngineComputesOutputShareVersusResultShare() {
        TrendFixture fixture = serviceWithTrendFixtures();

        var response = fixture.service().getBreakdownEngine(30, null, null, null, null, "platform", "engagements");
        var linkedInRow = response.getRows().stream()
                .filter(row -> "LINKEDIN".equals(row.getKey()))
                .findFirst()
                .orElseThrow();
        var youTubeRow = response.getRows().stream()
                .filter(row -> "YOUTUBE".equals(row.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(response.getDimension()).isEqualTo("platform");
        assertThat(response.getMetric()).isEqualTo("engagements");
        assertThat(response.getTotalPostsPublished()).isEqualTo(4L);
        assertThat(response.getTotalPerformanceValue()).isEqualTo(140.0);
        assertThat(linkedInRow.getPostsPublished()).isEqualTo(2L);
        assertThat(linkedInRow.getPerformanceValue()).isEqualTo(35.0);
        assertThat(linkedInRow.getOutputSharePercent()).isCloseTo(50.0, within(0.01));
        assertThat(linkedInRow.getPerformanceSharePercent()).isCloseTo(25.0, within(0.01));
        assertThat(linkedInRow.getShareGapPercent()).isCloseTo(-25.0, within(0.01));
        assertThat(youTubeRow.getOutputSharePercent()).isCloseTo(25.0, within(0.01));
        assertThat(youTubeRow.getPerformanceSharePercent()).isCloseTo(42.857, within(0.01));
    }

    private AnalyticsWorkspaceService serviceWithFixtures() {
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);

        AnalyticsWorkspaceService service = new AnalyticsWorkspaceService(
                postRepo,
                workspacePostAnalyticsRepo,
                accountAnalyticsSnapshotRepo,
                accountProfileService
        );

        WorkspaceContext.set("ws_1", WorkspaceRole.ADMIN);

        ConnectedAccount account = new ConnectedAccount();
        account.setPlatform(Platform.linkedin);
        account.setProviderUserId("acct_1");
        account.setUsername("Acme LinkedIn");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setId(11L);
        collection.setWorkspaceId("ws_1");
        collection.setDescription("Launch creative with product teaser");

        PostEntity currentPost = new PostEntity();
        currentPost.setId(101L);
        currentPost.setProvider(Provider.LINKEDIN);
        currentPost.setProviderUserId("acct_1");
        currentPost.setProviderPostId("urn:li:share:101");
        currentPost.setPostType(PostType.TEXT);
        currentPost.setScheduledTime(now.minusDays(2));
        currentPost.setPostCollection(collection);

        PostEntity previousPost = new PostEntity();
        previousPost.setId(102L);
        previousPost.setProvider(Provider.LINKEDIN);
        previousPost.setProviderUserId("acct_1");
        previousPost.setProviderPostId("urn:li:share:102");
        previousPost.setPostType(PostType.IMAGE);
        previousPost.setScheduledTime(now.minusDays(10));
        previousPost.setPostCollection(collection);

        WorkspacePostAnalyticsEntity currentMetrics = new WorkspacePostAnalyticsEntity();
        currentMetrics.setWorkspaceId("ws_1");
        currentMetrics.setPostId(101L);
        currentMetrics.setPostCollectionId(11L);
        currentMetrics.setProvider(Provider.LINKEDIN);
        currentMetrics.setProviderUserId("acct_1");
        currentMetrics.setProviderPostId("urn:li:share:101");
        currentMetrics.setPostType(PostType.TEXT);
        currentMetrics.setPublishedAt(now.minusDays(2));
        currentMetrics.setImpressions(100L);
        currentMetrics.setReach(88L);
        currentMetrics.setLikes(10L);
        currentMetrics.setComments(5L);
        currentMetrics.setShares(2L);
        currentMetrics.setClicks(8L);
        currentMetrics.setVideoViews(25L);
        currentMetrics.setEngagements(17L);
        currentMetrics.setEngagementRate(17.0);
        currentMetrics.setLastCollectedAt(now.minusHours(3));

        WorkspacePostAnalyticsEntity previousMetrics = new WorkspacePostAnalyticsEntity();
        previousMetrics.setWorkspaceId("ws_1");
        previousMetrics.setPostId(102L);
        previousMetrics.setPostCollectionId(11L);
        previousMetrics.setProvider(Provider.LINKEDIN);
        previousMetrics.setProviderUserId("acct_1");
        previousMetrics.setProviderPostId("urn:li:share:102");
        previousMetrics.setPostType(PostType.IMAGE);
        previousMetrics.setPublishedAt(now.minusDays(10));
        previousMetrics.setImpressions(40L);
        previousMetrics.setReach(31L);
        previousMetrics.setLikes(3L);
        previousMetrics.setComments(2L);
        previousMetrics.setShares(1L);
        previousMetrics.setClicks(4L);
        previousMetrics.setEngagements(6L);
        previousMetrics.setEngagementRate(15.0);
        previousMetrics.setLastCollectedAt(now.minusDays(9));

        AccountAnalyticsSnapshotEntity firstSnapshot = new AccountAnalyticsSnapshotEntity();
        firstSnapshot.setWorkspaceId("ws_1");
        firstSnapshot.setProviderUserId("acct_1");
        firstSnapshot.setProvider("LINKEDIN");
        firstSnapshot.setSnapshotDate(LocalDate.now(ZoneOffset.UTC).minusDays(2));
        firstSnapshot.setFollowers(200L);
        firstSnapshot.setPageViewsDay(120L);
        firstSnapshot.setUniquePageViewsDay(90L);
        firstSnapshot.setClicksDay(11L);

        AccountAnalyticsSnapshotEntity secondSnapshot = new AccountAnalyticsSnapshotEntity();
        secondSnapshot.setWorkspaceId("ws_1");
        secondSnapshot.setProviderUserId("acct_1");
        secondSnapshot.setProvider("LINKEDIN");
        secondSnapshot.setSnapshotDate(LocalDate.now(ZoneOffset.UTC).minusDays(1));
        secondSnapshot.setFollowers(230L);
        secondSnapshot.setPageViewsDay(70L);
        secondSnapshot.setUniquePageViewsDay(55L);
        secondSnapshot.setClicksDay(6L);

        when(accountProfileService.getAllConnectedAccounts("ws_1", false)).thenReturn(List.of(account));
        when(postRepo.findAnalyticsPostsByWorkspaceId("ws_1")).thenReturn(List.of(currentPost, previousPost));
        when(workspacePostAnalyticsRepo.findAllByWorkspaceId("ws_1"))
                .thenReturn(List.of(currentMetrics, previousMetrics));
        when(accountAnalyticsSnapshotRepo.findAllByWorkspaceId("ws_1"))
                .thenReturn(List.of(firstSnapshot, secondSnapshot));

        return service;
    }

    private AnalyticsWorkspaceService serviceWithYouTubeFixtures() {
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);

        AnalyticsWorkspaceService service = new AnalyticsWorkspaceService(
                postRepo,
                workspacePostAnalyticsRepo,
                accountAnalyticsSnapshotRepo,
                accountProfileService
        );

        WorkspaceContext.set("ws_1", WorkspaceRole.ADMIN);

        ConnectedAccount account = new ConnectedAccount();
        account.setPlatform(Platform.youtube);
        account.setProviderUserId("yt_channel_1");
        account.setUsername("Acme YouTube");

        when(accountProfileService.getAllConnectedAccounts("ws_1", false)).thenReturn(List.of(account));
        when(postRepo.findAnalyticsPostsByWorkspaceId("ws_1")).thenReturn(List.of());
        when(workspacePostAnalyticsRepo.findAllByWorkspaceId("ws_1")).thenReturn(List.of());

        AccountAnalyticsSnapshotEntity firstSnapshot = new AccountAnalyticsSnapshotEntity();
        firstSnapshot.setWorkspaceId("ws_1");
        firstSnapshot.setProviderUserId("yt_channel_1");
        firstSnapshot.setProvider("YOUTUBE");
        firstSnapshot.setSnapshotDate(LocalDate.now(ZoneOffset.UTC).minusDays(2));
        firstSnapshot.setFollowers(1237L);
        firstSnapshot.setVideoViewsDay(260L);
        firstSnapshot.setLikesDay(34L);
        firstSnapshot.setCommentsDay(9L);
        firstSnapshot.setSharesDay(5L);
        firstSnapshot.setWatchTimeMinutesDay(470L);
        firstSnapshot.setSubscriberDeltaDay(8L);

        AccountAnalyticsSnapshotEntity secondSnapshot = new AccountAnalyticsSnapshotEntity();
        secondSnapshot.setWorkspaceId("ws_1");
        secondSnapshot.setProviderUserId("yt_channel_1");
        secondSnapshot.setProvider("YOUTUBE");
        secondSnapshot.setSnapshotDate(LocalDate.now(ZoneOffset.UTC).minusDays(1));
        secondSnapshot.setFollowers(1250L);
        secondSnapshot.setVideoViewsDay(210L);
        secondSnapshot.setLikesDay(27L);
        secondSnapshot.setCommentsDay(8L);
        secondSnapshot.setSharesDay(4L);
        secondSnapshot.setWatchTimeMinutesDay(375L);
        secondSnapshot.setSubscriberDeltaDay(5L);

        when(accountAnalyticsSnapshotRepo.findAllByWorkspaceId("ws_1"))
                .thenReturn(List.of(firstSnapshot, secondSnapshot));

        return service;
    }

    private AnalyticsWorkspaceService serviceWithTikTokFixtures() {
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);

        AnalyticsWorkspaceService service = new AnalyticsWorkspaceService(
                postRepo,
                workspacePostAnalyticsRepo,
                accountAnalyticsSnapshotRepo,
                accountProfileService
        );

        WorkspaceContext.set("ws_1", WorkspaceRole.ADMIN);

        ConnectedAccount account = new ConnectedAccount();
        account.setPlatform(Platform.tiktok);
        account.setProviderUserId("tt_user_1");
        account.setUsername("Acme TikTok");

        AccountAnalyticsSnapshotEntity firstSnapshot = new AccountAnalyticsSnapshotEntity();
        firstSnapshot.setWorkspaceId("ws_1");
        firstSnapshot.setProviderUserId("tt_user_1");
        firstSnapshot.setProvider("TIKTOK");
        firstSnapshot.setSnapshotDate(LocalDate.now(ZoneOffset.UTC).minusDays(2));
        firstSnapshot.setFollowers(500L);
        firstSnapshot.setFollowing(111L);
        firstSnapshot.setLikesTotal(16_500L);
        firstSnapshot.setVideoCount(37L);

        AccountAnalyticsSnapshotEntity secondSnapshot = new AccountAnalyticsSnapshotEntity();
        secondSnapshot.setWorkspaceId("ws_1");
        secondSnapshot.setProviderUserId("tt_user_1");
        secondSnapshot.setProvider("TIKTOK");
        secondSnapshot.setSnapshotDate(LocalDate.now(ZoneOffset.UTC).minusDays(1));
        secondSnapshot.setFollowers(540L);
        secondSnapshot.setFollowing(118L);
        secondSnapshot.setLikesTotal(18_600L);
        secondSnapshot.setVideoCount(42L);

        when(accountProfileService.getAllConnectedAccounts("ws_1", false)).thenReturn(List.of(account));
        when(postRepo.findAnalyticsPostsByWorkspaceId("ws_1")).thenReturn(List.of());
        when(workspacePostAnalyticsRepo.findAllByWorkspaceId("ws_1")).thenReturn(List.of());
        when(accountAnalyticsSnapshotRepo.findAllByWorkspaceId("ws_1"))
                .thenReturn(List.of(firstSnapshot, secondSnapshot));

        return service;
    }

    private TrendFixture serviceWithTrendFixtures() {
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);

        AnalyticsWorkspaceService service = new AnalyticsWorkspaceService(
                postRepo,
                workspacePostAnalyticsRepo,
                accountAnalyticsSnapshotRepo,
                accountProfileService
        );

        WorkspaceContext.set("ws_1", WorkspaceRole.ADMIN);

        ConnectedAccount linkedInAccount = new ConnectedAccount();
        linkedInAccount.setPlatform(Platform.linkedin);
        linkedInAccount.setProviderUserId("li_page_1");
        linkedInAccount.setUsername("Acme LinkedIn");

        ConnectedAccount instagramAccount = new ConnectedAccount();
        instagramAccount.setPlatform(Platform.instagram);
        instagramAccount.setProviderUserId("ig_1");
        instagramAccount.setUsername("Acme Instagram");

        ConnectedAccount youTubeAccount = new ConnectedAccount();
        youTubeAccount.setPlatform(Platform.youtube);
        youTubeAccount.setProviderUserId("yt_1");
        youTubeAccount.setUsername("Acme YouTube");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime linkedInTextAt = now.minusDays(6).withHour(9).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime instagramImageAt = now.minusDays(5).withHour(14).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime youTubeVideoAt = now.minusDays(2).withHour(18).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime linkedInMixedAt = now.minusDays(1).withHour(1).withMinute(0).withSecond(0).withNano(0);

        PostCollectionEntity textCollection = collection(11L, "Text launch note", List.of());
        PostCollectionEntity imageCollection = collection(12L, "Image promo asset", List.of(media("promo.png", "image/png")));
        PostCollectionEntity videoCollection = collection(13L, "Video explainer drop", List.of(media("explainer.mp4", "video/mp4")));
        PostCollectionEntity mixedCollection = collection(
                14L,
                "Mixed media wrap-up",
                List.of(media("summary.png", "image/png"), media("summary.mp4", "video/mp4"))
        );

        PostEntity linkedInTextPost = analyticsPost(201L, Provider.LINKEDIN, "li_page_1", "li-201", PostType.TEXT, linkedInTextAt, textCollection);
        PostEntity instagramImagePost = analyticsPost(202L, Provider.INSTAGRAM, "ig_1", "ig-202", PostType.IMAGE, instagramImageAt, imageCollection);
        PostEntity youTubeVideoPost = analyticsPost(203L, Provider.YOUTUBE, "yt_1", "yt-203", PostType.VIDEO, youTubeVideoAt, videoCollection);
        PostEntity linkedInMixedPost = analyticsPost(204L, Provider.LINKEDIN, "li_page_1", "li-204", PostType.VIDEO, linkedInMixedAt, mixedCollection);

        WorkspacePostAnalyticsEntity linkedInTextMetrics = analyticsMetrics(201L, Provider.LINKEDIN, "li_page_1", "li-201", PostType.TEXT, linkedInTextAt);
        linkedInTextMetrics.setImpressions(100L);
        linkedInTextMetrics.setReach(85L);
        linkedInTextMetrics.setClicks(8L);
        linkedInTextMetrics.setLikes(12L);
        linkedInTextMetrics.setComments(5L);
        linkedInTextMetrics.setShares(3L);
        linkedInTextMetrics.setEngagements(20L);

        WorkspacePostAnalyticsEntity instagramImageMetrics = analyticsMetrics(202L, Provider.INSTAGRAM, "ig_1", "ig-202", PostType.IMAGE, instagramImageAt);
        instagramImageMetrics.setImpressions(300L);
        instagramImageMetrics.setReach(240L);
        instagramImageMetrics.setLikes(30L);
        instagramImageMetrics.setComments(10L);
        instagramImageMetrics.setShares(5L);
        instagramImageMetrics.setSaves(14L);
        instagramImageMetrics.setEngagements(45L);

        WorkspacePostAnalyticsEntity youTubeVideoMetrics = analyticsMetrics(203L, Provider.YOUTUBE, "yt_1", "yt-203", PostType.VIDEO, youTubeVideoAt);
        youTubeVideoMetrics.setImpressions(700L);
        youTubeVideoMetrics.setReach(540L);
        youTubeVideoMetrics.setLikes(40L);
        youTubeVideoMetrics.setComments(12L);
        youTubeVideoMetrics.setShares(8L);
        youTubeVideoMetrics.setVideoViews(500L);
        youTubeVideoMetrics.setWatchTimeMinutes(900L);
        youTubeVideoMetrics.setEngagements(60L);

        WorkspacePostAnalyticsEntity linkedInMixedMetrics = analyticsMetrics(204L, Provider.LINKEDIN, "li_page_1", "li-204", PostType.VIDEO, linkedInMixedAt);
        linkedInMixedMetrics.setImpressions(150L);
        linkedInMixedMetrics.setReach(120L);
        linkedInMixedMetrics.setLikes(8L);
        linkedInMixedMetrics.setComments(5L);
        linkedInMixedMetrics.setShares(2L);
        linkedInMixedMetrics.setVideoViews(140L);
        linkedInMixedMetrics.setEngagements(15L);

        when(accountProfileService.getAllConnectedAccounts("ws_1", false))
                .thenReturn(List.of(linkedInAccount, instagramAccount, youTubeAccount));
        when(postRepo.findAnalyticsPostsByWorkspaceId("ws_1"))
                .thenReturn(List.of(linkedInTextPost, instagramImagePost, youTubeVideoPost, linkedInMixedPost));
        when(workspacePostAnalyticsRepo.findAllByWorkspaceId("ws_1"))
                .thenReturn(List.of(linkedInTextMetrics, instagramImageMetrics, youTubeVideoMetrics, linkedInMixedMetrics));
        when(accountAnalyticsSnapshotRepo.findAllByWorkspaceId("ws_1")).thenReturn(List.of());

        return new TrendFixture(service, linkedInTextAt, instagramImageAt, youTubeVideoAt, linkedInMixedAt);
    }

    private PostCollectionEntity collection(Long id, String description, List<PostMediaEntity> mediaFiles) {
        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setId(id);
        collection.setWorkspaceId("ws_1");
        collection.setDescription(description);
        collection.setMediaFiles(mediaFiles);
        return collection;
    }

    private PostMediaEntity media(String fileName, String mimeType) {
        PostMediaEntity media = new PostMediaEntity();
        media.setFileName(fileName);
        media.setMimeType(mimeType);
        media.setFileKey(fileName);
        media.setSize(1L);
        return media;
    }

    private PostEntity analyticsPost(Long id,
                                     Provider provider,
                                     String providerUserId,
                                     String providerPostId,
                                     PostType postType,
                                     OffsetDateTime publishedAt,
                                     PostCollectionEntity collection) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setProvider(provider);
        post.setProviderUserId(providerUserId);
        post.setProviderPostId(providerPostId);
        post.setPostType(postType);
        post.setScheduledTime(publishedAt);
        post.setPostCollection(collection);
        return post;
    }

    private WorkspacePostAnalyticsEntity analyticsMetrics(Long postId,
                                                          Provider provider,
                                                          String providerUserId,
                                                          String providerPostId,
                                                          PostType postType,
                                                          OffsetDateTime publishedAt) {
        WorkspacePostAnalyticsEntity metrics = new WorkspacePostAnalyticsEntity();
        metrics.setWorkspaceId("ws_1");
        metrics.setPostId(postId);
        metrics.setProvider(provider);
        metrics.setProviderUserId(providerUserId);
        metrics.setProviderPostId(providerPostId);
        metrics.setPostType(postType);
        metrics.setPublishedAt(publishedAt);
        metrics.setLastCollectedAt(publishedAt.plusHours(2));
        return metrics;
    }

    private record TrendFixture(AnalyticsWorkspaceService service,
                                OffsetDateTime linkedInTextAt,
                                OffsetDateTime instagramImageAt,
                                OffsetDateTime youTubeVideoAt,
                                OffsetDateTime linkedInMixedAt) {
    }
}
