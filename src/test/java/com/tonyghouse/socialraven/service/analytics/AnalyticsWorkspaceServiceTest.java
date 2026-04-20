package com.tonyghouse.socialraven.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.AnalyticsFreshnessStatus;
import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.constant.PostType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewMetricResponse;
import com.tonyghouse.socialraven.entity.AnalyticsRecommendationEntity;
import com.tonyghouse.socialraven.entity.AccountAnalyticsSnapshotEntity;
import com.tonyghouse.socialraven.entity.PostAnalyticsSnapshotEntity;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.PostMediaEntity;
import com.tonyghouse.socialraven.entity.WorkspacePostAnalyticsEntity;
import com.tonyghouse.socialraven.repo.AccountAnalyticsSnapshotRepo;
import com.tonyghouse.socialraven.repo.AnalyticsRecommendationRepo;
import com.tonyghouse.socialraven.repo.PostAnalyticsSnapshotRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.repo.WorkspacePostAnalyticsRepo;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import java.time.LocalDate;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.util.ArrayList;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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

    @Test
    void patternLabWorkspaceBuildsEvidenceBackedRules() {
        PatternFixture fixture = serviceWithPatternFixtures();

        var response = fixture.service().getPatternLab(30, null, null, null, null, "workspace", "engagements");
        var context = response.getContexts().get(0);
        var hourPattern = context.getPostingWindowPatterns().stream()
                .filter(pattern -> "hourBucket".equals(pattern.getDimension()))
                .findFirst()
                .orElseThrow();
        var formatPattern = context.getFormatPatterns().stream()
                .filter(pattern -> "postType".equals(pattern.getDimension()))
                .findFirst()
                .orElseThrow();
        var accountPattern = context.getAccountPatterns().stream()
                .filter(pattern -> "YOUTUBE:yt_1".equals(pattern.getKey()))
                .findFirst()
                .orElseThrow();

        assertThat(response.getScope()).isEqualTo("workspace");
        assertThat(response.getMetric()).isEqualTo("engagements");
        assertThat(response.getMinimumSampleSize()).isEqualTo(3);
        assertThat(response.getEligiblePostCount()).isEqualTo(11L);
        assertThat(response.getExcludedPostCount()).isEqualTo(1L);
        assertThat(response.getContexts()).hasSize(1);
        assertThat(context.getContextLabel()).isEqualTo("Workspace");
        assertThat(context.getBaselineValue()).isCloseTo(47.636, within(0.01));
        assertThat(context.getPostingWindowPatterns()).isNotEmpty();
        assertThat(context.getFormatPatterns()).isNotEmpty();
        assertThat(context.getAccountPatterns()).isNotEmpty();
        assertThat(hourPattern.getSampleSize()).isEqualTo(4L);
        assertThat(hourPattern.getLiftPercent()).isGreaterThan(80.0);
        assertThat(formatPattern.getLabel()).isEqualTo("Video");
        assertThat(formatPattern.getConfidenceTier()).isEqualTo("MEDIUM");
        assertThat(accountPattern.getSampleSize()).isEqualTo(6L);
        assertThat(accountPattern.getConfidenceTier()).isEqualTo("MEDIUM");
        assertThat(accountPattern.getEvidenceSummary()).contains("posts averaged");
    }

    @Test
    void patternLabAccountScopeReturnsAccountSpecificContexts() {
        PatternFixture fixture = serviceWithPatternFixtures();

        var response = fixture.service().getPatternLab(30, null, null, null, null, "account", "engagements");
        var youTubeContext = response.getContexts().stream()
                .filter(context -> "YOUTUBE:yt_1".equals(context.getContextKey()))
                .findFirst()
                .orElseThrow();

        assertThat(response.getScope()).isEqualTo("account");
        assertThat(response.getContexts()).extracting("contextKey")
                .contains("YOUTUBE:yt_1", "LINKEDIN:li_1", "INSTAGRAM:ig_1");
        assertThat(youTubeContext.getEligiblePostCount()).isEqualTo(6L);
        assertThat(youTubeContext.getExcludedPostCount()).isEqualTo(0L);
        assertThat(youTubeContext.getPostingWindowPatterns()).isNotEmpty();
        assertThat(youTubeContext.getAccountPatterns()).isEmpty();
    }

    @Test
    void recommendationPanelBuildsAndPersistsWorkspaceRecommendations() {
        RecommendationFixture fixture = serviceWithRecommendationFixtures();

        var response = fixture.service().getRecommendationPanel(30, null, null, null, null, "workspace", "engagements");

        assertThat(response.getScope()).isEqualTo("workspace");
        assertThat(response.getMetric()).isEqualTo("engagements");
        assertThat(response.getRecommendations()).isNotEmpty();
        assertThat(response.getRecommendations()).allSatisfy(recommendation -> {
            assertThat(recommendation.getTitle()).isNotBlank();
            assertThat(recommendation.getActionSummary()).isNotBlank();
            assertThat(recommendation.getEvidenceSummary()).isNotBlank();
            assertThat(recommendation.getExpectedImpactScore()).isPositive();
            assertThat(recommendation.getTimeWindowStartAt()).isNotNull();
            assertThat(recommendation.getTimeWindowEndAt()).isNotNull();
        });
        assertThat(response.getRecommendations()).extracting("sourceType")
                .contains("POSTING_WINDOW_PATTERN", "FORMAT_PATTERN");
        assertThat(fixture.storedRecommendations()).isNotEmpty();
        assertThat(fixture.storedRecommendations()).allSatisfy(recommendation -> {
            assertThat(recommendation.getSliceKey()).contains("scope=workspace|metric=engagements");
            assertThat(recommendation.getSliceFingerprint()).isNotBlank();
            assertThat(recommendation.isActive()).isTrue();
        });
        assertThat(fixture.storedRecommendations()).extracting(AnalyticsRecommendationEntity::getSourceType)
                .contains("CONTRIBUTION_GAP");
    }

    @Test
    void dismissRecommendationHidesLowPriorityRecommendationWithoutDeletingIt() {
        RecommendationFixture fixture = serviceWithRecommendationFixtures();

        var initialPanel = fixture.service().getRecommendationPanel(30, null, null, null, null, "workspace", "engagements");
        var dismissibleRecommendation = initialPanel.getRecommendations().stream()
                .filter(recommendation -> recommendation.isDismissible())
                .findFirst()
                .orElseThrow();

        var dismissResponse = fixture.service().dismissRecommendation(dismissibleRecommendation.getId());
        var refreshedPanel = fixture.service().getRecommendationPanel(30, null, null, null, null, "workspace", "engagements");

        assertThat(dismissResponse.getRecommendationId()).isEqualTo(dismissibleRecommendation.getId());
        assertThat(dismissResponse.getDismissedAt()).isNotNull();
        assertThat(refreshedPanel.getDismissedRecommendationCount()).isEqualTo(1L);
        assertThat(refreshedPanel.getRecommendations()).extracting("id")
                .doesNotContain(dismissibleRecommendation.getId());

        AnalyticsRecommendationEntity storedRecommendation = fixture.storedRecommendations().stream()
                .filter(recommendation -> dismissibleRecommendation.getId().equals(recommendation.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(storedRecommendation.isActive()).isTrue();
        assertThat(storedRecommendation.getDismissedAt()).isNotNull();
    }

    @Test
    void recommendationPanelRegeneratesWhenTheUnderlyingSliceChanges() {
        RecommendationFixture fixture = serviceWithRecommendationFixtures();

        fixture.service().getRecommendationPanel(30, null, null, null, null, "workspace", "engagements");
        Set<String> initialFingerprints = fixture.storedRecommendations().stream()
                .map(AnalyticsRecommendationEntity::getSliceFingerprint)
                .collect(Collectors.toSet());
        long initialImpactScore = Math.round(fixture.storedRecommendations().stream()
                .mapToDouble(AnalyticsRecommendationEntity::getExpectedImpactScore)
                .sum());

        fixture.metrics().stream()
                .filter(metric -> Provider.YOUTUBE.equals(metric.getProvider()))
                .forEach(metric -> {
                    metric.setEngagements(metric.getEngagements() + 25L);
                    metric.setLikes(metric.getLikes() + 10L);
                    metric.setComments(metric.getComments() + 4L);
                    metric.setShares(metric.getShares() + 3L);
                    metric.setEngagementRate((metric.getEngagements().doubleValue() / metric.getImpressions()) * 100.0);
                });

        fixture.service().getRecommendationPanel(30, null, null, null, null, "workspace", "engagements");
        Set<String> refreshedFingerprints = fixture.storedRecommendations().stream()
                .map(AnalyticsRecommendationEntity::getSliceFingerprint)
                .collect(Collectors.toSet());
        long refreshedImpactScore = Math.round(fixture.storedRecommendations().stream()
                .mapToDouble(AnalyticsRecommendationEntity::getExpectedImpactScore)
                .sum());

        assertThat(refreshedFingerprints).isNotEqualTo(initialFingerprints);
        assertThat(refreshedImpactScore).isNotEqualTo(initialImpactScore);
    }

    @Test
    void forecastPanelBuildsNextPostSlotAndPlanningWindowForecasts() {
        PatternFixture fixture = serviceWithPatternFixtures();

        var response = fixture.service().getForecastPanel(30, null, null, null, null, "engagements", 7, 5);

        assertThat(response.getMetric()).isEqualTo("engagements");
        assertThat(response.getForecastDays()).isEqualTo(7);
        assertThat(response.getPlannedPosts()).isEqualTo(5);
        assertThat(response.getEligiblePostCount()).isEqualTo(11L);
        assertThat(response.getExcludedPostCount()).isEqualTo(1L);

        assertThat(response.getNextPostForecast().isAvailable()).isTrue();
        assertThat(response.getNextPostForecast().getComparablePosts()).isEqualTo(11L);
        assertThat(response.getNextPostForecast().getRange().getExpectedValue()).isCloseTo(47.6, within(0.2));
        assertThat(response.getNextPostForecast().getBasisSummary()).contains("11 comparable posts");

        assertThat(response.getNextBestSlot().isAvailable()).isTrue();
        assertThat(response.getNextBestSlot().getSlotLabel()).contains("16:00-19:59 UTC");
        assertThat(response.getNextBestSlot().getPredictedAt()).isNotNull();
        assertThat(response.getNextBestSlot().getObservedValue()).isCloseTo(90.0, within(0.2));
        assertThat(response.getNextBestSlot().getLiftPercent()).isGreaterThan(80.0);

        assertThat(response.getEndOfPeriodForecast().isAvailable()).isTrue();
        assertThat(response.getEndOfPeriodForecast().getPlannedPosts()).isEqualTo(5);
        assertThat(response.getEndOfPeriodForecast().getRange().getExpectedValue())
                .isGreaterThan(response.getNextPostForecast().getRange().getExpectedValue());
        assertThat(response.getEndOfPeriodForecast().getBasisSummary()).contains("5");
    }

    @Test
    void forecastPanelReturnsUnavailablePredictionsWhenHistoryIsTooSparse() {
        AnalyticsWorkspaceService service = serviceWithFixtures();

        var response = service.getForecastPanel(7, null, null, null, null, "engagements", 7, 3);

        assertThat(response.getEligiblePostCount()).isEqualTo(0L);
        assertThat(response.getNextPostForecast().isAvailable()).isFalse();
        assertThat(response.getNextPostForecast().getUnavailableReason()).contains("Need at least 8 comparable posts");
        assertThat(response.getNextBestSlot().isAvailable()).isFalse();
        assertThat(response.getEndOfPeriodForecast().isAvailable()).isFalse();
        assertThat(response.getEndOfPeriodForecast().getUnavailableReason()).contains("Need at least 8 comparable posts");
    }

    @Test
    void postDrilldownBuildsBenchmarksMilestonesAndComparablePosts() {
        DrilldownFixture fixture = serviceWithDrilldownFixtures();

        var response = fixture.service().getPostDrilldown(30, null, null, null, null, 501L, "engagements");

        assertThat(response.getPost().getPostId()).isEqualTo(501L);
        assertThat(response.getComparableBenchmark().getGroupLabel()).contains("YouTube video posts");
        assertThat(response.getComparableBenchmark().getComparableCount()).isEqualTo(3L);
        assertThat(response.getPercentileRank().getRank()).isEqualTo(2);
        assertThat(response.getPercentileRank().getComparableCount()).isEqualTo(3L);
        assertThat(response.getMilestoneProgression()).hasSize(3);
        assertThat(response.getMilestoneProgression()).extracting("engagements")
                .containsExactly(25L, 55L, 80L);
        assertThat(response.getComparablePosts()).extracting("postId")
                .containsExactly(502L, 503L);
    }

    @Test
    void accountPlatformAndCampaignDrilldownsBuildTrendContributionAndBenchmarks() {
        DrilldownFixture fixture = serviceWithDrilldownFixtures();

        var account = fixture.service().getAccountDrilldown(30, null, null, null, "youtube", "yt_1", "engagements");
        assertThat(account.getAccountName()).isEqualTo("Acme YouTube");
        assertThat(account.getSummary().getPostsPublished()).isEqualTo(3L);
        assertThat(account.getComparableBenchmark().getComparableCount()).isEqualTo(3L);
        assertThat(account.getPercentileRank().getComparableCount()).isEqualTo(3L);
        assertThat(account.getTrend()).isNotEmpty();
        assertThat(account.getPostTypeBreakdown().getRows()).hasSize(1);
        assertThat(account.getTopPosts()).extracting("postId")
                .containsExactly(502L, 501L, 503L);

        var platform = fixture.service().getPlatformDrilldown(30, null, null, null, null, "youtube", "engagements");
        assertThat(platform.getPlatformLabel()).isEqualTo("YouTube");
        assertThat(platform.getSummary().getPerformanceValue()).isEqualTo(210.0);
        assertThat(platform.getComparableBenchmark().getComparableCount()).isEqualTo(3L);
        assertThat(platform.getAccountBreakdown().getRows()).hasSize(1);
        assertThat(platform.getTopPosts()).extracting("postId")
                .containsExactly(502L, 501L, 503L);

        var campaign = fixture.service().getCampaignDrilldown(30, null, null, null, 52L, "engagements");
        assertThat(campaign.getCampaignLabel()).isEqualTo("Cross-channel launch recap");
        assertThat(campaign.getSummary().getPostsPublished()).isEqualTo(3L);
        assertThat(campaign.getComparableBenchmark().getComparableCount()).isEqualTo(3L);
        assertThat(campaign.getPlatformBreakdown().getRows()).extracting("key")
                .contains("YOUTUBE", "LINKEDIN");
        assertThat(campaign.getTopPosts()).extracting("postId")
                .containsExactly(503L, 505L, 504L);
    }

    private AnalyticsWorkspaceService serviceWithFixtures() {
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo = Mockito.mock(PostAnalyticsSnapshotRepo.class);
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);
        AnalyticsRecommendationRepo analyticsRecommendationRepo = Mockito.mock(AnalyticsRecommendationRepo.class);

        AnalyticsWorkspaceService service = new AnalyticsWorkspaceService(
                postRepo,
                workspacePostAnalyticsRepo,
                accountAnalyticsSnapshotRepo,
                postAnalyticsSnapshotRepo,
                accountProfileService,
                analyticsRecommendationRepo
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
        PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo = Mockito.mock(PostAnalyticsSnapshotRepo.class);
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);
        AnalyticsRecommendationRepo analyticsRecommendationRepo = Mockito.mock(AnalyticsRecommendationRepo.class);

        AnalyticsWorkspaceService service = new AnalyticsWorkspaceService(
                postRepo,
                workspacePostAnalyticsRepo,
                accountAnalyticsSnapshotRepo,
                postAnalyticsSnapshotRepo,
                accountProfileService,
                analyticsRecommendationRepo
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
        PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo = Mockito.mock(PostAnalyticsSnapshotRepo.class);
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);
        AnalyticsRecommendationRepo analyticsRecommendationRepo = Mockito.mock(AnalyticsRecommendationRepo.class);

        AnalyticsWorkspaceService service = new AnalyticsWorkspaceService(
                postRepo,
                workspacePostAnalyticsRepo,
                accountAnalyticsSnapshotRepo,
                postAnalyticsSnapshotRepo,
                accountProfileService,
                analyticsRecommendationRepo
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
        PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo = Mockito.mock(PostAnalyticsSnapshotRepo.class);
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);
        AnalyticsRecommendationRepo analyticsRecommendationRepo = Mockito.mock(AnalyticsRecommendationRepo.class);

        AnalyticsWorkspaceService service = new AnalyticsWorkspaceService(
                postRepo,
                workspacePostAnalyticsRepo,
                accountAnalyticsSnapshotRepo,
                postAnalyticsSnapshotRepo,
                accountProfileService,
                analyticsRecommendationRepo
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

    private PatternFixture serviceWithPatternFixtures() {
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo = Mockito.mock(PostAnalyticsSnapshotRepo.class);
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);
        AnalyticsRecommendationRepo analyticsRecommendationRepo = Mockito.mock(AnalyticsRecommendationRepo.class);

        AnalyticsWorkspaceService service = new AnalyticsWorkspaceService(
                postRepo,
                workspacePostAnalyticsRepo,
                accountAnalyticsSnapshotRepo,
                postAnalyticsSnapshotRepo,
                accountProfileService,
                analyticsRecommendationRepo
        );

        WorkspaceContext.set("ws_1", WorkspaceRole.ADMIN);

        ConnectedAccount linkedInAccount = new ConnectedAccount();
        linkedInAccount.setPlatform(Platform.linkedin);
        linkedInAccount.setProviderUserId("li_1");
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

        PostCollectionEntity linkedInCollection = collection(21L, "LinkedIn text notes", List.of());
        PostCollectionEntity instagramCollection = collection(22L, "Instagram image set", List.of(media("ig.png", "image/png")));
        PostCollectionEntity youTubeCollection = collection(23L, "YouTube video drops", List.of(media("yt.mp4", "video/mp4")));
        PostCollectionEntity excludedCollection = collection(24L, "Excluded unpublished metrics row", List.of());

        List<PostEntity> posts = List.of(
                analyticsPost(301L, Provider.YOUTUBE, "yt_1", "yt-301", PostType.VIDEO, now.minusDays(6).withHour(18).withMinute(0).withSecond(0).withNano(0), youTubeCollection),
                analyticsPost(302L, Provider.YOUTUBE, "yt_1", "yt-302", PostType.VIDEO, now.minusDays(13).withHour(18).withMinute(0).withSecond(0).withNano(0), youTubeCollection),
                analyticsPost(303L, Provider.YOUTUBE, "yt_1", "yt-303", PostType.VIDEO, now.minusDays(20).withHour(18).withMinute(0).withSecond(0).withNano(0), youTubeCollection),
                analyticsPost(304L, Provider.YOUTUBE, "yt_1", "yt-304", PostType.VIDEO, now.minusDays(27).withHour(18).withMinute(0).withSecond(0).withNano(0), youTubeCollection),
                analyticsPost(305L, Provider.YOUTUBE, "yt_1", "yt-305", PostType.VIDEO, now.minusDays(4).withHour(10).withMinute(0).withSecond(0).withNano(0), youTubeCollection),
                analyticsPost(306L, Provider.YOUTUBE, "yt_1", "yt-306", PostType.VIDEO, now.minusDays(11).withHour(10).withMinute(0).withSecond(0).withNano(0), youTubeCollection),
                analyticsPost(307L, Provider.LINKEDIN, "li_1", "li-307", PostType.TEXT, now.minusDays(5).withHour(9).withMinute(0).withSecond(0).withNano(0), linkedInCollection),
                analyticsPost(308L, Provider.LINKEDIN, "li_1", "li-308", PostType.TEXT, now.minusDays(12).withHour(9).withMinute(0).withSecond(0).withNano(0), linkedInCollection),
                analyticsPost(309L, Provider.LINKEDIN, "li_1", "li-309", PostType.TEXT, now.minusDays(19).withHour(9).withMinute(0).withSecond(0).withNano(0), linkedInCollection),
                analyticsPost(310L, Provider.INSTAGRAM, "ig_1", "ig-310", PostType.IMAGE, now.minusDays(7).withHour(14).withMinute(0).withSecond(0).withNano(0), instagramCollection),
                analyticsPost(311L, Provider.INSTAGRAM, "ig_1", "ig-311", PostType.IMAGE, now.minusDays(14).withHour(14).withMinute(0).withSecond(0).withNano(0), instagramCollection),
                analyticsPost(312L, Provider.LINKEDIN, "li_1", "li-312", PostType.TEXT, now.minusDays(2).withHour(16).withMinute(0).withSecond(0).withNano(0), excludedCollection)
        );

        List<WorkspacePostAnalyticsEntity> metrics = List.of(
                engagementMetrics(301L, Provider.YOUTUBE, "yt_1", "yt-301", PostType.VIDEO, posts.get(0).getScheduledTime(), 90L),
                engagementMetrics(302L, Provider.YOUTUBE, "yt_1", "yt-302", PostType.VIDEO, posts.get(1).getScheduledTime(), 90L),
                engagementMetrics(303L, Provider.YOUTUBE, "yt_1", "yt-303", PostType.VIDEO, posts.get(2).getScheduledTime(), 90L),
                engagementMetrics(304L, Provider.YOUTUBE, "yt_1", "yt-304", PostType.VIDEO, posts.get(3).getScheduledTime(), 90L),
                engagementMetrics(305L, Provider.YOUTUBE, "yt_1", "yt-305", PostType.VIDEO, posts.get(4).getScheduledTime(), 30L),
                engagementMetrics(306L, Provider.YOUTUBE, "yt_1", "yt-306", PostType.VIDEO, posts.get(5).getScheduledTime(), 30L),
                engagementMetrics(307L, Provider.LINKEDIN, "li_1", "li-307", PostType.TEXT, posts.get(6).getScheduledTime(), 18L),
                engagementMetrics(308L, Provider.LINKEDIN, "li_1", "li-308", PostType.TEXT, posts.get(7).getScheduledTime(), 18L),
                engagementMetrics(309L, Provider.LINKEDIN, "li_1", "li-309", PostType.TEXT, posts.get(8).getScheduledTime(), 18L),
                engagementMetrics(310L, Provider.INSTAGRAM, "ig_1", "ig-310", PostType.IMAGE, posts.get(9).getScheduledTime(), 25L),
                engagementMetrics(311L, Provider.INSTAGRAM, "ig_1", "ig-311", PostType.IMAGE, posts.get(10).getScheduledTime(), 25L)
        );

        when(accountProfileService.getAllConnectedAccounts("ws_1", false))
                .thenReturn(List.of(linkedInAccount, instagramAccount, youTubeAccount));
        when(postRepo.findAnalyticsPostsByWorkspaceId("ws_1")).thenReturn(posts);
        when(workspacePostAnalyticsRepo.findAllByWorkspaceId("ws_1")).thenReturn(metrics);
        when(accountAnalyticsSnapshotRepo.findAllByWorkspaceId("ws_1")).thenReturn(List.of());

        return new PatternFixture(service);
    }

    private RecommendationFixture serviceWithRecommendationFixtures() {
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo = Mockito.mock(PostAnalyticsSnapshotRepo.class);
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);
        AnalyticsRecommendationRepo analyticsRecommendationRepo = Mockito.mock(AnalyticsRecommendationRepo.class);

        List<AnalyticsRecommendationEntity> storedRecommendations = new ArrayList<>();
        AtomicLong recommendationIds = new AtomicLong(1L);

        when(analyticsRecommendationRepo.findAllByWorkspaceIdAndSliceKeyOrderByExpectedImpactScoreDesc(Mockito.eq("ws_1"), Mockito.anyString()))
                .thenAnswer(invocation -> storedRecommendations.stream()
                        .filter(recommendation -> "ws_1".equals(recommendation.getWorkspaceId()))
                        .filter(recommendation -> invocation.getArgument(1, String.class).equals(recommendation.getSliceKey()))
                        .sorted(Comparator.comparingDouble(AnalyticsRecommendationEntity::getExpectedImpactScore).reversed())
                        .toList());
        when(analyticsRecommendationRepo.findByIdAndWorkspaceId(Mockito.anyLong(), Mockito.eq("ws_1")))
                .thenAnswer(invocation -> storedRecommendations.stream()
                        .filter(recommendation -> invocation.getArgument(0, Long.class).equals(recommendation.getId()))
                        .findFirst());
        when(analyticsRecommendationRepo.save(Mockito.any(AnalyticsRecommendationEntity.class)))
                .thenAnswer(invocation -> saveRecommendation(
                        storedRecommendations,
                        recommendationIds,
                        invocation.getArgument(0, AnalyticsRecommendationEntity.class)
                ));
        when(analyticsRecommendationRepo.saveAll(Mockito.anyList()))
                .thenAnswer(invocation -> {
                    List<AnalyticsRecommendationEntity> recommendations = invocation.getArgument(0);
                    recommendations.forEach(recommendation -> saveRecommendation(
                            storedRecommendations,
                            recommendationIds,
                            recommendation
                    ));
                    return recommendations;
                });

        AnalyticsWorkspaceService service = new AnalyticsWorkspaceService(
                postRepo,
                workspacePostAnalyticsRepo,
                accountAnalyticsSnapshotRepo,
                postAnalyticsSnapshotRepo,
                accountProfileService,
                analyticsRecommendationRepo
        );

        WorkspaceContext.set("ws_1", WorkspaceRole.ADMIN);

        ConnectedAccount linkedInAccount = new ConnectedAccount();
        linkedInAccount.setPlatform(Platform.linkedin);
        linkedInAccount.setProviderUserId("li_1");
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
        PostCollectionEntity linkedInCollection = collection(31L, "LinkedIn text notes", List.of());
        PostCollectionEntity instagramCollection = collection(32L, "Instagram image set", List.of(media("ig.png", "image/png")));
        PostCollectionEntity youTubeCollection = collection(33L, "YouTube video drops", List.of(media("yt.mp4", "video/mp4")));
        PostCollectionEntity weakVideoCollection = collection(34L, "Weak video posts", List.of(media("weak.mp4", "video/mp4")));

        List<PostEntity> posts = List.of(
                analyticsPost(401L, Provider.YOUTUBE, "yt_1", "yt-401", PostType.VIDEO, now.minusDays(6).withHour(18).withMinute(0).withSecond(0).withNano(0), youTubeCollection),
                analyticsPost(402L, Provider.YOUTUBE, "yt_1", "yt-402", PostType.VIDEO, now.minusDays(13).withHour(18).withMinute(0).withSecond(0).withNano(0), youTubeCollection),
                analyticsPost(403L, Provider.YOUTUBE, "yt_1", "yt-403", PostType.VIDEO, now.minusDays(20).withHour(18).withMinute(0).withSecond(0).withNano(0), youTubeCollection),
                analyticsPost(404L, Provider.YOUTUBE, "yt_1", "yt-404", PostType.VIDEO, now.minusDays(27).withHour(18).withMinute(0).withSecond(0).withNano(0), youTubeCollection),
                analyticsPost(405L, Provider.YOUTUBE, "yt_1", "yt-405", PostType.VIDEO, now.minusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0), weakVideoCollection),
                analyticsPost(406L, Provider.YOUTUBE, "yt_1", "yt-406", PostType.VIDEO, now.minusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0), weakVideoCollection),
                analyticsPost(407L, Provider.LINKEDIN, "li_1", "li-407", PostType.TEXT, now.minusDays(5).withHour(9).withMinute(0).withSecond(0).withNano(0), linkedInCollection),
                analyticsPost(408L, Provider.LINKEDIN, "li_1", "li-408", PostType.TEXT, now.minusDays(12).withHour(9).withMinute(0).withSecond(0).withNano(0), linkedInCollection),
                analyticsPost(409L, Provider.LINKEDIN, "li_1", "li-409", PostType.TEXT, now.minusDays(19).withHour(9).withMinute(0).withSecond(0).withNano(0), linkedInCollection),
                analyticsPost(410L, Provider.INSTAGRAM, "ig_1", "ig-410", PostType.IMAGE, now.minusDays(7).withHour(14).withMinute(0).withSecond(0).withNano(0), instagramCollection),
                analyticsPost(411L, Provider.INSTAGRAM, "ig_1", "ig-411", PostType.IMAGE, now.minusDays(14).withHour(14).withMinute(0).withSecond(0).withNano(0), instagramCollection)
        );

        List<WorkspacePostAnalyticsEntity> metrics = new ArrayList<>(List.of(
                engagementMetrics(401L, Provider.YOUTUBE, "yt_1", "yt-401", PostType.VIDEO, posts.get(0).getScheduledTime(), 95L),
                engagementMetrics(402L, Provider.YOUTUBE, "yt_1", "yt-402", PostType.VIDEO, posts.get(1).getScheduledTime(), 95L),
                engagementMetrics(403L, Provider.YOUTUBE, "yt_1", "yt-403", PostType.VIDEO, posts.get(2).getScheduledTime(), 95L),
                engagementMetrics(404L, Provider.YOUTUBE, "yt_1", "yt-404", PostType.VIDEO, posts.get(3).getScheduledTime(), 95L),
                engagementMetrics(405L, Provider.YOUTUBE, "yt_1", "yt-405", PostType.VIDEO, posts.get(4).getScheduledTime(), 18L),
                engagementMetrics(406L, Provider.YOUTUBE, "yt_1", "yt-406", PostType.VIDEO, posts.get(5).getScheduledTime(), 18L),
                engagementMetrics(407L, Provider.LINKEDIN, "li_1", "li-407", PostType.TEXT, posts.get(6).getScheduledTime(), 14L),
                engagementMetrics(408L, Provider.LINKEDIN, "li_1", "li-408", PostType.TEXT, posts.get(7).getScheduledTime(), 14L),
                engagementMetrics(409L, Provider.LINKEDIN, "li_1", "li-409", PostType.TEXT, posts.get(8).getScheduledTime(), 14L),
                engagementMetrics(410L, Provider.INSTAGRAM, "ig_1", "ig-410", PostType.IMAGE, posts.get(9).getScheduledTime(), 26L),
                engagementMetrics(411L, Provider.INSTAGRAM, "ig_1", "ig-411", PostType.IMAGE, posts.get(10).getScheduledTime(), 26L)
        ));

        when(accountProfileService.getAllConnectedAccounts("ws_1", false))
                .thenReturn(List.of(linkedInAccount, instagramAccount, youTubeAccount));
        when(postRepo.findAnalyticsPostsByWorkspaceId("ws_1")).thenReturn(posts);
        when(workspacePostAnalyticsRepo.findAllByWorkspaceId("ws_1")).thenAnswer(invocation -> metrics);
        when(accountAnalyticsSnapshotRepo.findAllByWorkspaceId("ws_1")).thenReturn(List.of());

        return new RecommendationFixture(service, metrics, storedRecommendations);
    }

    private DrilldownFixture serviceWithDrilldownFixtures() {
        PostRepo postRepo = Mockito.mock(PostRepo.class);
        WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo = Mockito.mock(WorkspacePostAnalyticsRepo.class);
        AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo = Mockito.mock(AccountAnalyticsSnapshotRepo.class);
        PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo = Mockito.mock(PostAnalyticsSnapshotRepo.class);
        AccountProfileService accountProfileService = Mockito.mock(AccountProfileService.class);
        AnalyticsRecommendationRepo analyticsRecommendationRepo = Mockito.mock(AnalyticsRecommendationRepo.class);

        AnalyticsWorkspaceService service = new AnalyticsWorkspaceService(
                postRepo,
                workspacePostAnalyticsRepo,
                accountAnalyticsSnapshotRepo,
                postAnalyticsSnapshotRepo,
                accountProfileService,
                analyticsRecommendationRepo
        );

        WorkspaceContext.set("ws_1", WorkspaceRole.ADMIN);

        ConnectedAccount linkedInAccount = new ConnectedAccount();
        linkedInAccount.setPlatform(Platform.linkedin);
        linkedInAccount.setProviderUserId("li_1");
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

        PostCollectionEntity campaign51 = collection(51L, "YouTube launch burst", List.of(media("launch.mp4", "video/mp4")));
        PostCollectionEntity campaign52 = collection(52L, "Cross-channel launch recap", List.of());
        PostCollectionEntity campaign53 = collection(53L, "Instagram carousel push", List.of(media("ig.png", "image/png")));

        List<PostEntity> posts = List.of(
                analyticsPost(501L, Provider.YOUTUBE, "yt_1", "yt-501", PostType.VIDEO, now.minusDays(5).withHour(18).withMinute(0).withSecond(0).withNano(0), campaign51),
                analyticsPost(502L, Provider.YOUTUBE, "yt_1", "yt-502", PostType.VIDEO, now.minusDays(12).withHour(18).withMinute(0).withSecond(0).withNano(0), campaign51),
                analyticsPost(503L, Provider.YOUTUBE, "yt_1", "yt-503", PostType.VIDEO, now.minusDays(20).withHour(10).withMinute(0).withSecond(0).withNano(0), campaign52),
                analyticsPost(504L, Provider.LINKEDIN, "li_1", "li-504", PostType.TEXT, now.minusDays(4).withHour(9).withMinute(0).withSecond(0).withNano(0), campaign52),
                analyticsPost(505L, Provider.LINKEDIN, "li_1", "li-505", PostType.TEXT, now.minusDays(13).withHour(9).withMinute(0).withSecond(0).withNano(0), campaign52),
                analyticsPost(506L, Provider.INSTAGRAM, "ig_1", "ig-506", PostType.IMAGE, now.minusDays(8).withHour(14).withMinute(0).withSecond(0).withNano(0), campaign53),
                analyticsPost(507L, Provider.INSTAGRAM, "ig_1", "ig-507", PostType.IMAGE, now.minusDays(18).withHour(14).withMinute(0).withSecond(0).withNano(0), campaign53)
        );

        List<WorkspacePostAnalyticsEntity> metrics = List.of(
                engagementMetrics(501L, Provider.YOUTUBE, "yt_1", "yt-501", PostType.VIDEO, posts.get(0).getScheduledTime(), 80L),
                engagementMetrics(502L, Provider.YOUTUBE, "yt_1", "yt-502", PostType.VIDEO, posts.get(1).getScheduledTime(), 90L),
                engagementMetrics(503L, Provider.YOUTUBE, "yt_1", "yt-503", PostType.VIDEO, posts.get(2).getScheduledTime(), 40L),
                engagementMetrics(504L, Provider.LINKEDIN, "li_1", "li-504", PostType.TEXT, posts.get(3).getScheduledTime(), 20L),
                engagementMetrics(505L, Provider.LINKEDIN, "li_1", "li-505", PostType.TEXT, posts.get(4).getScheduledTime(), 22L),
                engagementMetrics(506L, Provider.INSTAGRAM, "ig_1", "ig-506", PostType.IMAGE, posts.get(5).getScheduledTime(), 35L),
                engagementMetrics(507L, Provider.INSTAGRAM, "ig_1", "ig-507", PostType.IMAGE, posts.get(6).getScheduledTime(), 38L)
        );

        List<PostAnalyticsSnapshotEntity> post501Snapshots = List.of(
                postSnapshot(501L, 51L, "yt_1", "yt-501", PostType.VIDEO, posts.get(0).getScheduledTime(), posts.get(0).getScheduledTime().plusHours(2), 25L),
                postSnapshot(501L, 51L, "yt_1", "yt-501", PostType.VIDEO, posts.get(0).getScheduledTime(), posts.get(0).getScheduledTime().plusDays(1).plusHours(2), 55L),
                postSnapshot(501L, 51L, "yt_1", "yt-501", PostType.VIDEO, posts.get(0).getScheduledTime(), posts.get(0).getScheduledTime().plusDays(3).plusHours(2), 80L)
        );

        when(accountProfileService.getAllConnectedAccounts("ws_1", false))
                .thenReturn(List.of(linkedInAccount, instagramAccount, youTubeAccount));
        when(postRepo.findAnalyticsPostsByWorkspaceId("ws_1")).thenReturn(posts);
        when(workspacePostAnalyticsRepo.findAllByWorkspaceId("ws_1")).thenReturn(metrics);
        when(accountAnalyticsSnapshotRepo.findAllByWorkspaceId("ws_1")).thenReturn(List.of());
        when(postAnalyticsSnapshotRepo.findAllByPostId(501L)).thenReturn(post501Snapshots);
        when(postAnalyticsSnapshotRepo.findAllByPostId(Mockito.longThat(id -> !Long.valueOf(501L).equals(id))))
                .thenReturn(List.of());

        return new DrilldownFixture(service);
    }

    private AnalyticsRecommendationEntity saveRecommendation(List<AnalyticsRecommendationEntity> store,
                                                             AtomicLong recommendationIds,
                                                             AnalyticsRecommendationEntity recommendation) {
        if (recommendation.getId() == null) {
            recommendation.setId(recommendationIds.getAndIncrement());
        }

        Optional<AnalyticsRecommendationEntity> existing = store.stream()
                .filter(item -> recommendation.getId().equals(item.getId()))
                .findFirst();
        existing.ifPresent(store::remove);
        store.add(recommendation);
        return recommendation;
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
        metrics.setFreshnessStatus(AnalyticsFreshnessStatus.FRESH);
        metrics.setLastCollectedAt(publishedAt.plusHours(2));
        return metrics;
    }

    private WorkspacePostAnalyticsEntity engagementMetrics(Long postId,
                                                           Provider provider,
                                                           String providerUserId,
                                                           String providerPostId,
                                                           PostType postType,
                                                           OffsetDateTime publishedAt,
                                                           Long engagements) {
        WorkspacePostAnalyticsEntity metrics = analyticsMetrics(
                postId,
                provider,
                providerUserId,
                providerPostId,
                postType,
                publishedAt
        );
        metrics.setImpressions(engagements * 10L);
        metrics.setReach(engagements * 8L);
        metrics.setLikes(Math.max(engagements - 8L, 1L));
        metrics.setComments(5L);
        metrics.setShares(3L);
        metrics.setEngagements(engagements);
        metrics.setEngagementRate((engagements.doubleValue() / metrics.getImpressions()) * 100.0);
        return metrics;
    }

    private PostAnalyticsSnapshotEntity postSnapshot(Long postId,
                                                     Long postCollectionId,
                                                     String providerUserId,
                                                     String providerPostId,
                                                     PostType postType,
                                                     OffsetDateTime publishedAt,
                                                     OffsetDateTime fetchedAt,
                                                     Long engagements) {
        PostAnalyticsSnapshotEntity snapshot = new PostAnalyticsSnapshotEntity();
        snapshot.setWorkspaceId("ws_1");
        snapshot.setPostId(postId);
        snapshot.setPostCollectionId(postCollectionId);
        snapshot.setProvider(Provider.YOUTUBE.name());
        snapshot.setProviderUserId(providerUserId);
        snapshot.setProviderPostId(providerPostId);
        snapshot.setSnapshotType("LATEST");
        snapshot.setPostType(postType.name());
        snapshot.setPublishedAt(publishedAt);
        snapshot.setImpressions(engagements * 10L);
        snapshot.setReach(engagements * 8L);
        snapshot.setLikes(Math.max(engagements - 12L, 1L));
        snapshot.setComments(5L);
        snapshot.setShares(3L);
        snapshot.setVideoViews(engagements * 6L);
        snapshot.setEngagements(engagements);
        snapshot.setEngagementRate((engagements.doubleValue() / snapshot.getImpressions()) * 100.0);
        snapshot.setFetchedAt(fetchedAt);
        return snapshot;
    }

    private record TrendFixture(AnalyticsWorkspaceService service,
                                OffsetDateTime linkedInTextAt,
                                OffsetDateTime instagramImageAt,
                                OffsetDateTime youTubeVideoAt,
                                OffsetDateTime linkedInMixedAt) {
    }

    private record PatternFixture(AnalyticsWorkspaceService service) {
    }

    private record RecommendationFixture(AnalyticsWorkspaceService service,
                                         List<WorkspacePostAnalyticsEntity> metrics,
                                         List<AnalyticsRecommendationEntity> storedRecommendations) {
    }

    private record DrilldownFixture(AnalyticsWorkspaceService service) {
    }
}
