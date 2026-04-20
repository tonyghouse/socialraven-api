package com.tonyghouse.socialraven.service.analytics;

import com.tonyghouse.socialraven.constant.AnalyticsFreshnessStatus;
import com.tonyghouse.socialraven.constant.PostType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsBreakdownResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsBreakdownRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsCampaignDrilldownResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsComparableBenchmarkResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsDrilldownContributionResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsDrilldownSummaryResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsEndOfPeriodForecastResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsForecastBestSlotResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsForecastPanelResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsForecastPredictionResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsForecastRangeResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsAccountDrilldownResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsLinkedInPageActivityResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsLinkedInPageActivityRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewMetricResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPatternContextResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPatternLabResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPatternResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPercentileRankResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPlatformDrilldownResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostDrilldownResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostMilestonePointResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostRankingsResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostTableResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsRecommendationDismissResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsRecommendationPanelResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsRecommendationResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTikTokCreatorActivityResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTikTokCreatorActivityRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTikTokCreatorActivityTrendPointResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTrendExplorerPointResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTrendExplorerResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsYouTubeChannelActivityResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsYouTubeChannelActivityRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsYouTubeChannelActivityTrendPointResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsWorkspaceOverviewResponse;
import com.tonyghouse.socialraven.entity.AnalyticsRecommendationEntity;
import com.tonyghouse.socialraven.entity.AccountAnalyticsSnapshotEntity;
import com.tonyghouse.socialraven.entity.PostAnalyticsSnapshotEntity;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.WorkspacePostAnalyticsEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.model.AnalyticsMetricAvailabilityWindow;
import com.tonyghouse.socialraven.repo.AccountAnalyticsSnapshotRepo;
import com.tonyghouse.socialraven.repo.AnalyticsRecommendationRepo;
import com.tonyghouse.socialraven.repo.PostAnalyticsSnapshotRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.repo.WorkspacePostAnalyticsRepo;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AnalyticsWorkspaceService {

    private final PostRepo postRepo;
    private final WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo;
    private final AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo;
    private final PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo;
    private final AccountProfileService accountProfileService;
    private final AnalyticsRecommendationRepo analyticsRecommendationRepo;

    @Transactional(readOnly = true)
    public AnalyticsWorkspaceOverviewResponse getOverview(int days,
                                                          String platform,
                                                          String providerUserId,
                                                          Long campaignId,
                                                          String contentType) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, campaignId, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);

        OverviewAggregate current = aggregate(dataset.currentRows());
        OverviewAggregate previous = aggregate(dataset.previousRows());

        return new AnalyticsWorkspaceOverviewResponse(
                slice.currentRangeLabel(),
                slice.previousRangeLabel(),
                slice.currentStartAt(),
                slice.currentEndAt(),
                slice.previousStartAt(),
                slice.previousEndAt(),
                List.of(
                        metric("impressions", "Impressions", "number", current.impressions(), previous.impressions()),
                        metric("reach", "Reach", "number", current.reach(), previous.reach()),
                        metric("engagements", "Engagements", "number", current.engagements(), previous.engagements()),
                        metric("engagementRate", "Engagement Rate", "percent", current.engagementRate(), previous.engagementRate()),
                        metric("clicks", "Clicks", "number", current.clicks(), previous.clicks()),
                        metric("videoViews", "Video Views", "number", current.videoViews(), previous.videoViews()),
                        metric("watchTimeMinutes", "Watch Time (min)", "number", current.watchTimeMinutes(), previous.watchTimeMinutes()),
                        metric("postsPublished", "Posts Published", "number", current.postsPublished(), previous.postsPublished())
                )
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsPostTableResponse getPostTable(int days,
                                                   String platform,
                                                   String providerUserId,
                                                   Long campaignId,
                                                   String contentType,
                                                   String sortBy,
                                                   String sortDirection,
                                                   int page,
                                                   int size) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, campaignId, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDirection = normalizeSortDirection(sortDirection);

        List<AnalyticsPostRowResponse> sortedRows = dataset.currentRows().stream()
                .sorted(buildComparator(normalizedSortBy, normalizedSortDirection))
                .toList();

        int fromIndex = Math.min(safePage * safeSize, sortedRows.size());
        int toIndex = Math.min(fromIndex + safeSize, sortedRows.size());

        return new AnalyticsPostTableResponse(
                normalizedSortBy,
                normalizedSortDirection,
                safePage,
                safeSize,
                sortedRows.size(),
                toIndex < sortedRows.size(),
                sortedRows.subList(fromIndex, toIndex)
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsPostRankingsResponse getPostRankings(int days,
                                                         String platform,
                                                         String providerUserId,
                                                         Long campaignId,
                                                         String contentType,
                                                         String metric,
                                                         int limit) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, campaignId, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);
        String normalizedMetric = normalizeRankingMetric(metric);
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        ToDoubleFunction<AnalyticsPostRowResponse> metricExtractor = rankingMetricExtractor(normalizedMetric);
        List<AnalyticsPostRowResponse> candidates = dataset.currentRows().stream()
                .filter(row -> hasRankingMetric(row, normalizedMetric))
                .toList();

        List<AnalyticsPostRowResponse> topPosts = candidates.stream()
                .sorted(Comparator.comparingDouble(metricExtractor).reversed()
                        .thenComparing(AnalyticsPostRowResponse::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AnalyticsPostRowResponse::getPostId, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(safeLimit)
                .toList();

        List<AnalyticsPostRowResponse> worstPosts = candidates.stream()
                .sorted(Comparator.comparingDouble(metricExtractor)
                        .thenComparing(AnalyticsPostRowResponse::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AnalyticsPostRowResponse::getPostId, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(safeLimit)
                .toList();

        return new AnalyticsPostRankingsResponse(normalizedMetric, topPosts, worstPosts);
    }

    @Transactional(readOnly = true)
    public AnalyticsTrendExplorerResponse getTrendExplorer(int days,
                                                           String platform,
                                                           String providerUserId,
                                                           Long campaignId,
                                                           String contentType,
                                                           String metric) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, campaignId, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);
        String normalizedMetric = normalizeTrendMetric(metric);
        MetricDefinition metricDefinition = metricDefinition(normalizedMetric);

        double totalPerformanceValue = computePerformanceValue(dataset.currentRows(), normalizedMetric);
        long totalPostsPublished = dataset.currentRows().size();

        return new AnalyticsTrendExplorerResponse(
                slice.currentRangeLabel(),
                slice.currentStartAt(),
                slice.currentEndAt(),
                normalizedMetric,
                metricDefinition.label(),
                metricDefinition.format(),
                totalPerformanceValue,
                totalPostsPublished,
                totalPostsPublished > 0 ? totalPerformanceValue / totalPostsPublished : null,
                buildTrendPoints(
                        dataset.currentRows(),
                        normalizedMetric,
                        slice.currentStartAt().toLocalDate(),
                        slice.currentEndAt().toLocalDate(),
                        1
                ),
                buildTrendPoints(
                        dataset.currentRows(),
                        normalizedMetric,
                        slice.currentStartAt().toLocalDate(),
                        slice.currentEndAt().toLocalDate(),
                        7
                )
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsBreakdownResponse getBreakdownEngine(int days,
                                                         String platform,
                                                         String providerUserId,
                                                         Long campaignId,
                                                         String contentType,
                                                         String dimension,
                                                         String metric) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, campaignId, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);
        String normalizedDimension = normalizeBreakdownDimension(dimension);
        String normalizedMetric = normalizeBreakdownMetric(metric);
        MetricDefinition metricDefinition = metricDefinition(normalizedMetric);
        long totalPostsPublished = dataset.currentRows().size();
        double totalPerformanceValue = computePerformanceValue(dataset.currentRows(), normalizedMetric);

        return new AnalyticsBreakdownResponse(
                slice.currentRangeLabel(),
                slice.currentStartAt(),
                slice.currentEndAt(),
                normalizedDimension,
                breakdownDimensionLabel(normalizedDimension),
                normalizedMetric,
                metricDefinition.label(),
                metricDefinition.format(),
                totalPostsPublished,
                totalPerformanceValue,
                totalPostsPublished > 0 ? totalPerformanceValue / totalPostsPublished : null,
                buildBreakdownRows(
                        dataset.currentRows(),
                        normalizedDimension,
                        normalizedMetric,
                        totalPostsPublished,
                        totalPerformanceValue
                )
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsPatternLabResponse getPatternLab(int days,
                                                     String platform,
                                                     String providerUserId,
                                                     Long campaignId,
                                                     String contentType,
                                                     String scope,
                                                     String metric) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, campaignId, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);
        String normalizedScope = normalizePatternScope(scope);
        String normalizedMetric = normalizePatternMetric(metric);
        MetricDefinition metricDefinition = metricDefinition(normalizedMetric);
        int minimumSampleSize = minimumPatternSampleSize();
        List<AnalyticsPostRowResponse> currentRows = dataset.currentRows();
        List<AnalyticsPostRowResponse> eligibleRows = currentRows.stream()
                .filter(row -> isPatternEligible(row, normalizedMetric))
                .toList();

        return new AnalyticsPatternLabResponse(
                slice.currentRangeLabel(),
                slice.currentStartAt(),
                slice.currentEndAt(),
                normalizedScope,
                patternScopeLabel(normalizedScope),
                normalizedMetric,
                metricDefinition.label(),
                metricDefinition.format(),
                minimumSampleSize,
                eligibleRows.size(),
                Math.max(currentRows.size() - eligibleRows.size(), 0),
                "Only posts with live metric coverage for the selected metric are included. Stale rows and provider-incomplete rows are excluded from pattern output.",
                buildPatternContexts(currentRows, normalizedScope, normalizedMetric, minimumSampleSize)
        );
    }

    @Transactional
    public AnalyticsRecommendationPanelResponse getRecommendationPanel(int days,
                                                                       String platform,
                                                                       String providerUserId,
                                                                       Long campaignId,
                                                                       String contentType,
                                                                       String scope,
                                                                       String metric) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, campaignId, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);
        String normalizedScope = normalizePatternScope(scope);
        String normalizedMetric = normalizePatternMetric(metric);
        MetricDefinition metricDefinition = metricDefinition(normalizedMetric);

        List<AnalyticsRecommendationEntity> sliceRecommendations = regenerateRecommendationsIfNeeded(
                slice,
                dataset.currentRows(),
                normalizedScope,
                normalizedMetric
        );
        List<AnalyticsRecommendationEntity> activeRecommendations = sliceRecommendations.stream()
                .filter(AnalyticsRecommendationEntity::isActive)
                .sorted(Comparator.comparingDouble(AnalyticsRecommendationEntity::getExpectedImpactScore).reversed()
                        .thenComparing(AnalyticsRecommendationEntity::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        long dismissedRecommendationCount = activeRecommendations.stream()
                .filter(recommendation -> recommendation.getDismissedAt() != null)
                .count();

        List<AnalyticsRecommendationResponse> recommendations = activeRecommendations.stream()
                .filter(recommendation -> recommendation.getDismissedAt() == null)
                .limit(6)
                .map(this::toRecommendationResponse)
                .toList();

        return new AnalyticsRecommendationPanelResponse(
                slice.currentRangeLabel(),
                slice.currentStartAt(),
                slice.currentEndAt(),
                normalizedScope,
                patternScopeLabel(normalizedScope),
                normalizedMetric,
                metricDefinition.label(),
                metricDefinition.format(),
                activeRecommendations.size(),
                dismissedRecommendationCount,
                recommendations
        );
    }

    @Transactional
    public AnalyticsRecommendationDismissResponse dismissRecommendation(Long recommendationId) {
        String workspaceId = requireWorkspaceId();
        AnalyticsRecommendationEntity recommendation = analyticsRecommendationRepo.findByIdAndWorkspaceId(recommendationId, workspaceId)
                .orElseThrow(() -> new SocialRavenException("Recommendation not found", HttpStatus.NOT_FOUND));

        if (!recommendation.isActive()) {
            throw new SocialRavenException("Recommendation is no longer active for this slice", HttpStatus.CONFLICT);
        }
        if (!recommendationDismissible(recommendation.getPriority())) {
            throw new SocialRavenException("Only low-priority recommendations can be dismissed", HttpStatus.BAD_REQUEST);
        }

        if (recommendation.getDismissedAt() == null) {
            recommendation.setDismissedAt(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
            analyticsRecommendationRepo.save(recommendation);
        }

        return new AnalyticsRecommendationDismissResponse(recommendation.getId(), recommendation.getDismissedAt());
    }

    @Transactional(readOnly = true)
    public AnalyticsForecastPanelResponse getForecastPanel(int days,
                                                           String platform,
                                                           String providerUserId,
                                                           Long campaignId,
                                                           String contentType,
                                                           String metric,
                                                           int forecastDays,
                                                           int plannedPosts) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, campaignId, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);
        String normalizedMetric = normalizeForecastMetric(metric);
        MetricDefinition metricDefinition = metricDefinition(normalizedMetric);
        int safeForecastDays = normalizeForecastDays(forecastDays);
        int safePlannedPosts = normalizePlannedPosts(plannedPosts);
        int minimumComparablePosts = minimumForecastComparablePosts();
        int minimumSlotSampleSize = minimumForecastSlotSampleSize();

        List<AnalyticsPostRowResponse> currentRows = dataset.currentRows();
        List<AnalyticsPostRowResponse> eligibleRows = currentRows.stream()
                .filter(row -> isForecastEligible(row, normalizedMetric))
                .toList();

        AnalyticsForecastPredictionResponse nextPostForecast = buildNextPostForecast(
                eligibleRows,
                normalizedMetric,
                minimumComparablePosts
        );
        AnalyticsForecastBestSlotResponse nextBestSlot = buildNextBestSlotForecast(
                eligibleRows,
                normalizedMetric,
                safeForecastDays,
                minimumComparablePosts,
                minimumSlotSampleSize
        );
        AnalyticsEndOfPeriodForecastResponse endOfPeriodForecast = buildEndOfPeriodForecast(
                eligibleRows,
                normalizedMetric,
                slice.days(),
                safeForecastDays,
                safePlannedPosts,
                minimumComparablePosts,
                nextPostForecast,
                nextBestSlot
        );

        return new AnalyticsForecastPanelResponse(
                slice.currentRangeLabel(),
                slice.currentStartAt(),
                slice.currentEndAt(),
                normalizedMetric,
                metricDefinition.label(),
                metricDefinition.format(),
                safeForecastDays,
                planningWindowLabel(safeForecastDays),
                safePlannedPosts,
                minimumComparablePosts,
                minimumSlotSampleSize,
                eligibleRows.size(),
                Math.max(currentRows.size() - eligibleRows.size(), 0),
                "Forecasts use only fresh posts with live metric coverage in the current workspace slice. Sparse, stale, and provider-incomplete rows are excluded.",
                nextPostForecast,
                nextBestSlot,
                endOfPeriodForecast
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsPostDrilldownResponse getPostDrilldown(int days,
                                                           String platform,
                                                           String providerUserId,
                                                           Long campaignId,
                                                           String contentType,
                                                           Long postId,
                                                           String metric) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, campaignId, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);
        String normalizedMetric = normalizeRankingMetric(metric);
        MetricDefinition metricDefinition = metricDefinition(normalizedMetric);

        AnalyticsPostRowResponse targetPost = dataset.currentRows().stream()
                .filter(row -> Objects.equals(postId, row.getPostId()))
                .findFirst()
                .orElseThrow(() -> new SocialRavenException("Post drilldown not found for this workspace slice", HttpStatus.NOT_FOUND));

        PostComparableGroup comparableGroup = resolvePostComparableGroup(dataset.currentRows(), targetPost, normalizedMetric);

        return new AnalyticsPostDrilldownResponse(
                slice.currentRangeLabel(),
                slice.currentStartAt(),
                slice.currentEndAt(),
                normalizedMetric,
                metricDefinition.label(),
                metricDefinition.format(),
                targetPost,
                buildPostComparableBenchmark(targetPost, comparableGroup, dataset.currentRows(), normalizedMetric),
                buildPostPercentileRank(targetPost, comparableGroup.rows(), normalizedMetric),
                buildMilestoneProgression(postId),
                comparableGroup.rows().stream()
                        .filter(row -> !Objects.equals(targetPost.getPostId(), row.getPostId()))
                        .sorted(Comparator.comparingDouble(
                                (AnalyticsPostRowResponse row) -> metricValueOrZero(row, normalizedMetric)
                        ).reversed().thenComparing(
                                AnalyticsPostRowResponse::getPublishedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                        .limit(5)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsAccountDrilldownResponse getAccountDrilldown(int days,
                                                                 String platform,
                                                                 Long campaignId,
                                                                 String contentType,
                                                                 String provider,
                                                                 String targetProviderUserId,
                                                                 String metric) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, null, campaignId, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);
        Provider targetProvider = Provider.valueOf(provider.toUpperCase(Locale.ENGLISH));
        String normalizedMetric = normalizeBreakdownMetric(metric);
        MetricDefinition metricDefinition = metricDefinition(normalizedMetric);

        List<AnalyticsPostRowResponse> targetRows = dataset.currentRows().stream()
                .filter(row -> targetProvider.name().equals(row.getProvider()))
                .filter(row -> targetProviderUserId.equals(row.getProviderUserId()))
                .toList();
        if (targetRows.isEmpty()) {
            throw new SocialRavenException("Account drilldown not found for this workspace slice", HttpStatus.NOT_FOUND);
        }

        long totalPostsPublished = dataset.currentRows().size();
        double totalPerformanceValue = computePerformanceValue(dataset.currentRows(), normalizedMetric);
        List<AnalyticsBreakdownRowResponse> accountRows = buildBreakdownRows(
                dataset.currentRows(),
                "account",
                normalizedMetric,
                totalPostsPublished,
                totalPerformanceValue
        );
        String accountKey = accountKey(targetProvider, targetProviderUserId);
        AnalyticsBreakdownRowResponse summaryRow = findBreakdownRow(accountRows, accountKey, "Account drilldown not found for this workspace slice");

        return new AnalyticsAccountDrilldownResponse(
                slice.currentRangeLabel(),
                slice.currentStartAt(),
                slice.currentEndAt(),
                normalizedMetric,
                metricDefinition.label(),
                metricDefinition.format(),
                targetProvider.name(),
                targetProviderUserId,
                targetRows.get(0).getAccountName(),
                buildGroupComparableBenchmark(
                        "Accounts in this slice",
                        "Average " + metricDefinition.label().toLowerCase(Locale.ENGLISH) + " per post",
                        summaryRow,
                        accountRows
                ),
                buildGroupPercentileRank(summaryRow, accountRows),
                toDrilldownSummary(summaryRow),
                buildTrendPoints(
                        targetRows,
                        normalizedMetric,
                        slice.currentStartAt().toLocalDate(),
                        slice.currentEndAt().toLocalDate(),
                        1
                ),
                buildContributionSection(targetRows, "postType", normalizedMetric),
                buildContributionSection(targetRows, "mediaFormat", normalizedMetric),
                topPosts(targetRows, normalizedMetric)
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsPlatformDrilldownResponse getPlatformDrilldown(int days,
                                                                   String platform,
                                                                   String providerUserId,
                                                                   Long campaignId,
                                                                   String contentType,
                                                                   String provider,
                                                                   String metric) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, campaignId, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);
        Provider targetProvider = Provider.valueOf(provider.toUpperCase(Locale.ENGLISH));
        String normalizedMetric = normalizeBreakdownMetric(metric);
        MetricDefinition metricDefinition = metricDefinition(normalizedMetric);

        List<AnalyticsPostRowResponse> targetRows = dataset.currentRows().stream()
                .filter(row -> targetProvider.name().equals(row.getProvider()))
                .toList();
        if (targetRows.isEmpty()) {
            throw new SocialRavenException("Platform drilldown not found for this workspace slice", HttpStatus.NOT_FOUND);
        }

        long totalPostsPublished = dataset.currentRows().size();
        double totalPerformanceValue = computePerformanceValue(dataset.currentRows(), normalizedMetric);
        List<AnalyticsBreakdownRowResponse> platformRows = buildBreakdownRows(
                dataset.currentRows(),
                "platform",
                normalizedMetric,
                totalPostsPublished,
                totalPerformanceValue
        );
        AnalyticsBreakdownRowResponse summaryRow = findBreakdownRow(platformRows, targetProvider.name(), "Platform drilldown not found for this workspace slice");

        return new AnalyticsPlatformDrilldownResponse(
                slice.currentRangeLabel(),
                slice.currentStartAt(),
                slice.currentEndAt(),
                normalizedMetric,
                metricDefinition.label(),
                metricDefinition.format(),
                targetProvider.name(),
                labelForProvider(targetProvider.name()),
                buildGroupComparableBenchmark(
                        "Platforms in this slice",
                        "Average " + metricDefinition.label().toLowerCase(Locale.ENGLISH) + " per post",
                        summaryRow,
                        platformRows
                ),
                buildGroupPercentileRank(summaryRow, platformRows),
                toDrilldownSummary(summaryRow),
                buildTrendPoints(
                        targetRows,
                        normalizedMetric,
                        slice.currentStartAt().toLocalDate(),
                        slice.currentEndAt().toLocalDate(),
                        1
                ),
                buildContributionSection(targetRows, "account", normalizedMetric),
                buildContributionSection(targetRows, "postType", normalizedMetric),
                buildContributionSection(targetRows, "mediaFormat", normalizedMetric),
                topPosts(targetRows, normalizedMetric)
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsCampaignDrilldownResponse getCampaignDrilldown(int days,
                                                                   String platform,
                                                                   String providerUserId,
                                                                   String contentType,
                                                                   Long targetCampaignId,
                                                                   String metric) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, null, contentType);
        WorkspaceAnalyticsDataset dataset = loadDataset(slice);
        String normalizedMetric = normalizeBreakdownMetric(metric);
        MetricDefinition metricDefinition = metricDefinition(normalizedMetric);

        List<AnalyticsPostRowResponse> targetRows = dataset.currentRows().stream()
                .filter(row -> Objects.equals(targetCampaignId, row.getCampaignId()))
                .toList();
        if (targetRows.isEmpty()) {
            throw new SocialRavenException("Campaign drilldown not found for this workspace slice", HttpStatus.NOT_FOUND);
        }

        long totalPostsPublished = dataset.currentRows().size();
        double totalPerformanceValue = computePerformanceValue(dataset.currentRows(), normalizedMetric);
        List<AnalyticsBreakdownRowResponse> campaignRows = buildBreakdownRows(
                dataset.currentRows(),
                "campaign",
                normalizedMetric,
                totalPostsPublished,
                totalPerformanceValue
        );
        AnalyticsBreakdownRowResponse summaryRow = findBreakdownRow(
                campaignRows,
                String.valueOf(targetCampaignId),
                "Campaign drilldown not found for this workspace slice"
        );

        return new AnalyticsCampaignDrilldownResponse(
                slice.currentRangeLabel(),
                slice.currentStartAt(),
                slice.currentEndAt(),
                normalizedMetric,
                metricDefinition.label(),
                metricDefinition.format(),
                targetCampaignId,
                targetRows.get(0).getCampaignLabel(),
                buildGroupComparableBenchmark(
                        "Campaigns in this slice",
                        "Average " + metricDefinition.label().toLowerCase(Locale.ENGLISH) + " per post",
                        summaryRow,
                        campaignRows
                ),
                buildGroupPercentileRank(summaryRow, campaignRows),
                toDrilldownSummary(summaryRow),
                buildTrendPoints(
                        targetRows,
                        normalizedMetric,
                        slice.currentStartAt().toLocalDate(),
                        slice.currentEndAt().toLocalDate(),
                        1
                ),
                buildContributionSection(targetRows, "platform", normalizedMetric),
                buildContributionSection(targetRows, "account", normalizedMetric),
                topPosts(targetRows, normalizedMetric)
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsLinkedInPageActivityResponse getLinkedInPageActivity(int days,
                                                                        String platform,
                                                                        String providerUserId) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, null, null);
        if (slice.provider() != null && !Provider.LINKEDIN.equals(slice.provider())) {
            return new AnalyticsLinkedInPageActivityResponse(
                    slice.currentRangeLabel(),
                    slice.currentStartAt().toLocalDate(),
                    slice.currentEndAt().toLocalDate(),
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    List.of()
            );
        }

        String workspaceId = requireWorkspaceId();
        LocalDate fromDate = slice.currentStartAt().toLocalDate();
        LocalDate toDate = slice.currentEndAt().toLocalDate();

        Map<String, ConnectedAccount> linkedInAccountsById = accountProfileService.getAllConnectedAccounts(workspaceId, false)
                .stream()
                .filter(account -> Provider.LINKEDIN.equals(Provider.valueOf(account.getPlatform().name().toUpperCase())))
                .filter(account -> !StringUtils.hasText(providerUserId) || providerUserId.equals(account.getProviderUserId()))
                .collect(Collectors.toMap(
                        ConnectedAccount::getProviderUserId,
                        account -> account,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        if (linkedInAccountsById.isEmpty()) {
            return new AnalyticsLinkedInPageActivityResponse(
                    slice.currentRangeLabel(),
                    fromDate,
                    toDate,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    List.of()
            );
        }

        List<AccountAnalyticsSnapshotEntity> snapshots = accountAnalyticsSnapshotRepo.findAllByWorkspaceId(workspaceId)
                .stream()
                .filter(snapshot -> Provider.LINKEDIN.name().equals(snapshot.getProvider()))
                .filter(snapshot -> linkedInAccountsById.containsKey(snapshot.getProviderUserId()))
                .filter(snapshot -> !snapshot.getSnapshotDate().isBefore(fromDate))
                .filter(snapshot -> !snapshot.getSnapshotDate().isAfter(toDate))
                .toList();

        Map<String, List<AccountAnalyticsSnapshotEntity>> snapshotsByAccount = snapshots.stream()
                .collect(Collectors.groupingBy(
                        AccountAnalyticsSnapshotEntity::getProviderUserId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        long totalPageViews = 0L;
        long totalUniquePageViews = 0L;
        long totalClicks = 0L;
        long totalFollowerDelta = 0L;
        List<LinkedInPageActivityAggregate> aggregates = new ArrayList<>();

        for (Map.Entry<String, ConnectedAccount> entry : linkedInAccountsById.entrySet()) {
            List<AccountAnalyticsSnapshotEntity> accountSnapshots = new ArrayList<>(
                    snapshotsByAccount.getOrDefault(entry.getKey(), List.of())
            );
            accountSnapshots.sort(Comparator.comparing(AccountAnalyticsSnapshotEntity::getSnapshotDate));

            Long pageViews = sumAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getPageViewsDay);
            Long uniquePageViews = sumAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getUniquePageViewsDay);
            Long clicks = sumAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getClicksDay);
            Long followers = latestAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getFollowers);
            Long followerDelta = computeFollowerDelta(accountSnapshots);
            LocalDate lastSnapshotDate = accountSnapshots.isEmpty()
                    ? null
                    : accountSnapshots.get(accountSnapshots.size() - 1).getSnapshotDate();

            totalPageViews += pageViews != null ? pageViews : 0L;
            totalUniquePageViews += uniquePageViews != null ? uniquePageViews : 0L;
            totalClicks += clicks != null ? clicks : 0L;
            totalFollowerDelta += followerDelta != null ? followerDelta : 0L;

            aggregates.add(new LinkedInPageActivityAggregate(
                    entry.getKey(),
                    entry.getValue().getUsername(),
                    followers,
                    followerDelta,
                    pageViews,
                    uniquePageViews,
                    clicks,
                    lastSnapshotDate
            ));
        }

        long safeTotalPageViews = totalPageViews;
        long safeTotalClicks = totalClicks;
        List<AnalyticsLinkedInPageActivityRowResponse> rows = aggregates.stream()
                .sorted(Comparator.comparing(LinkedInPageActivityAggregate::pageViews, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LinkedInPageActivityAggregate::clicks, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LinkedInPageActivityAggregate::accountName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(aggregate -> new AnalyticsLinkedInPageActivityRowResponse(
                        aggregate.providerUserId(),
                        aggregate.accountName(),
                        aggregate.followers(),
                        aggregate.followerDelta(),
                        aggregate.pageViews(),
                        aggregate.uniquePageViews(),
                        aggregate.clicks(),
                        ratioPercent(aggregate.pageViews(), safeTotalPageViews),
                        ratioPercent(aggregate.clicks(), safeTotalClicks),
                        aggregate.lastSnapshotDate()
                ))
                .toList();

        return new AnalyticsLinkedInPageActivityResponse(
                slice.currentRangeLabel(),
                fromDate,
                toDate,
                linkedInAccountsById.size(),
                totalPageViews,
                totalUniquePageViews,
                totalClicks,
                totalFollowerDelta,
                rows
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsYouTubeChannelActivityResponse getYouTubeChannelActivity(int days,
                                                                             String platform,
                                                                             String providerUserId) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, null, null);
        if (slice.provider() != null && !Provider.YOUTUBE.equals(slice.provider())) {
            return new AnalyticsYouTubeChannelActivityResponse(
                    slice.currentRangeLabel(),
                    slice.currentStartAt().toLocalDate(),
                    slice.currentEndAt().toLocalDate(),
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    List.of(),
                    List.of()
            );
        }

        String workspaceId = requireWorkspaceId();
        LocalDate fromDate = slice.currentStartAt().toLocalDate();
        LocalDate toDate = slice.currentEndAt().toLocalDate();

        Map<String, ConnectedAccount> youTubeAccountsById = accountProfileService.getAllConnectedAccounts(workspaceId, false)
                .stream()
                .filter(account -> Provider.YOUTUBE.equals(Provider.valueOf(account.getPlatform().name().toUpperCase())))
                .filter(account -> !StringUtils.hasText(providerUserId) || providerUserId.equals(account.getProviderUserId()))
                .collect(Collectors.toMap(
                        ConnectedAccount::getProviderUserId,
                        account -> account,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        if (youTubeAccountsById.isEmpty()) {
            return new AnalyticsYouTubeChannelActivityResponse(
                    slice.currentRangeLabel(),
                    fromDate,
                    toDate,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    List.of(),
                    List.of()
            );
        }

        List<AccountAnalyticsSnapshotEntity> snapshots = accountAnalyticsSnapshotRepo.findAllByWorkspaceId(workspaceId)
                .stream()
                .filter(snapshot -> Provider.YOUTUBE.name().equals(snapshot.getProvider()))
                .filter(snapshot -> youTubeAccountsById.containsKey(snapshot.getProviderUserId()))
                .filter(snapshot -> !snapshot.getSnapshotDate().isBefore(fromDate))
                .filter(snapshot -> !snapshot.getSnapshotDate().isAfter(toDate))
                .toList();

        Map<String, List<AccountAnalyticsSnapshotEntity>> snapshotsByAccount = snapshots.stream()
                .collect(Collectors.groupingBy(
                        AccountAnalyticsSnapshotEntity::getProviderUserId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        long totalVideoViews = 0L;
        long totalLikes = 0L;
        long totalComments = 0L;
        long totalShares = 0L;
        long totalWatchTimeMinutes = 0L;
        long totalSubscriberDelta = 0L;
        List<YouTubeChannelActivityAggregate> aggregates = new ArrayList<>();

        for (Map.Entry<String, ConnectedAccount> entry : youTubeAccountsById.entrySet()) {
            List<AccountAnalyticsSnapshotEntity> accountSnapshots = new ArrayList<>(
                    snapshotsByAccount.getOrDefault(entry.getKey(), List.of())
            );
            accountSnapshots.sort(Comparator.comparing(AccountAnalyticsSnapshotEntity::getSnapshotDate));

            Long followers = latestAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getFollowers);
            Long videoViews = sumAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getVideoViewsDay);
            Long likes = sumAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getLikesDay);
            Long comments = sumAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getCommentsDay);
            Long shares = sumAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getSharesDay);
            Long watchTimeMinutes = sumAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getWatchTimeMinutesDay);
            Long subscriberDelta = sumAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getSubscriberDeltaDay);
            LocalDate lastSnapshotDate = accountSnapshots.isEmpty()
                    ? null
                    : accountSnapshots.get(accountSnapshots.size() - 1).getSnapshotDate();

            totalVideoViews += value(videoViews);
            totalLikes += value(likes);
            totalComments += value(comments);
            totalShares += value(shares);
            totalWatchTimeMinutes += value(watchTimeMinutes);
            totalSubscriberDelta += value(subscriberDelta);

            aggregates.add(new YouTubeChannelActivityAggregate(
                    entry.getKey(),
                    entry.getValue().getUsername(),
                    followers,
                    subscriberDelta,
                    videoViews,
                    likes,
                    comments,
                    shares,
                    watchTimeMinutes,
                    lastSnapshotDate
            ));
        }

        long safeTotalVideoViews = totalVideoViews;
        long safeTotalWatchTimeMinutes = totalWatchTimeMinutes;
        List<AnalyticsYouTubeChannelActivityRowResponse> rows = aggregates.stream()
                .sorted(Comparator.comparing(YouTubeChannelActivityAggregate::watchTimeMinutes, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(YouTubeChannelActivityAggregate::videoViews, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(YouTubeChannelActivityAggregate::accountName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(aggregate -> new AnalyticsYouTubeChannelActivityRowResponse(
                        aggregate.providerUserId(),
                        aggregate.accountName(),
                        aggregate.followers(),
                        aggregate.subscriberDelta(),
                        aggregate.videoViews(),
                        aggregate.likes(),
                        aggregate.comments(),
                        aggregate.shares(),
                        aggregate.watchTimeMinutes(),
                        ratioPercent(aggregate.videoViews(), safeTotalVideoViews),
                        ratioPercent(aggregate.watchTimeMinutes(), safeTotalWatchTimeMinutes),
                        aggregate.lastSnapshotDate()
                ))
                .toList();

        Map<LocalDate, List<AccountAnalyticsSnapshotEntity>> snapshotsByDate = snapshots.stream()
                .collect(Collectors.groupingBy(
                        AccountAnalyticsSnapshotEntity::getSnapshotDate,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<AnalyticsYouTubeChannelActivityTrendPointResponse> trend = snapshotsByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AnalyticsYouTubeChannelActivityTrendPointResponse(
                        entry.getKey(),
                        sumAccountMetric(entry.getValue(), AccountAnalyticsSnapshotEntity::getVideoViewsDay),
                        sumAccountMetric(entry.getValue(), AccountAnalyticsSnapshotEntity::getWatchTimeMinutesDay),
                        sumAccountMetric(entry.getValue(), AccountAnalyticsSnapshotEntity::getSubscriberDeltaDay)
                ))
                .toList();

        return new AnalyticsYouTubeChannelActivityResponse(
                slice.currentRangeLabel(),
                fromDate,
                toDate,
                youTubeAccountsById.size(),
                totalVideoViews,
                totalLikes,
                totalComments,
                totalShares,
                totalWatchTimeMinutes,
                totalSubscriberDelta,
                trend,
                rows
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsTikTokCreatorActivityResponse getTikTokCreatorActivity(int days,
                                                                           String platform,
                                                                           String providerUserId) {
        WorkspaceAnalyticsSlice slice = WorkspaceAnalyticsSlice.of(days, platform, providerUserId, null, null);
        if (slice.provider() != null && !Provider.TIKTOK.equals(slice.provider())) {
            return emptyTikTokCreatorActivity(slice);
        }

        String workspaceId = requireWorkspaceId();
        LocalDate fromDate = slice.currentStartAt().toLocalDate();
        LocalDate toDate = slice.currentEndAt().toLocalDate();

        Map<String, ConnectedAccount> tikTokAccountsById = accountProfileService.getAllConnectedAccounts(workspaceId, false)
                .stream()
                .filter(account -> Provider.TIKTOK.equals(Provider.valueOf(account.getPlatform().name().toUpperCase())))
                .filter(account -> !StringUtils.hasText(providerUserId) || providerUserId.equals(account.getProviderUserId()))
                .collect(Collectors.toMap(
                        ConnectedAccount::getProviderUserId,
                        account -> account,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        if (tikTokAccountsById.isEmpty()) {
            return emptyTikTokCreatorActivity(slice);
        }

        List<AccountAnalyticsSnapshotEntity> snapshots = accountAnalyticsSnapshotRepo.findAllByWorkspaceId(workspaceId)
                .stream()
                .filter(snapshot -> Provider.TIKTOK.name().equals(snapshot.getProvider()))
                .filter(snapshot -> tikTokAccountsById.containsKey(snapshot.getProviderUserId()))
                .filter(snapshot -> !snapshot.getSnapshotDate().isBefore(fromDate))
                .filter(snapshot -> !snapshot.getSnapshotDate().isAfter(toDate))
                .toList();

        Map<String, List<AccountAnalyticsSnapshotEntity>> snapshotsByAccount = snapshots.stream()
                .collect(Collectors.groupingBy(
                        AccountAnalyticsSnapshotEntity::getProviderUserId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        long totalFollowers = 0L;
        long totalFollowing = 0L;
        long totalLikesTotal = 0L;
        long totalVideoCount = 0L;
        long totalFollowerDelta = 0L;
        long totalLikesDelta = 0L;
        long totalVideoDelta = 0L;
        List<TikTokCreatorActivityAggregate> aggregates = new ArrayList<>();

        for (Map.Entry<String, ConnectedAccount> entry : tikTokAccountsById.entrySet()) {
            List<AccountAnalyticsSnapshotEntity> accountSnapshots = new ArrayList<>(
                    snapshotsByAccount.getOrDefault(entry.getKey(), List.of())
            );
            accountSnapshots.sort(Comparator.comparing(AccountAnalyticsSnapshotEntity::getSnapshotDate));

            Long followers = latestAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getFollowers);
            Long following = latestAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getFollowing);
            Long likesTotal = latestAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getLikesTotal);
            Long videoCount = latestAccountMetric(accountSnapshots, AccountAnalyticsSnapshotEntity::getVideoCount);
            Long followerDelta = computeMetricDelta(accountSnapshots, AccountAnalyticsSnapshotEntity::getFollowers);
            Long likesDelta = computeMetricDelta(accountSnapshots, AccountAnalyticsSnapshotEntity::getLikesTotal);
            Long videoDelta = computeMetricDelta(accountSnapshots, AccountAnalyticsSnapshotEntity::getVideoCount);
            LocalDate lastSnapshotDate = accountSnapshots.isEmpty()
                    ? null
                    : accountSnapshots.get(accountSnapshots.size() - 1).getSnapshotDate();

            totalFollowers += value(followers);
            totalFollowing += value(following);
            totalLikesTotal += value(likesTotal);
            totalVideoCount += value(videoCount);
            totalFollowerDelta += value(followerDelta);
            totalLikesDelta += value(likesDelta);
            totalVideoDelta += value(videoDelta);

            aggregates.add(new TikTokCreatorActivityAggregate(
                    entry.getKey(),
                    entry.getValue().getUsername(),
                    followers,
                    following,
                    likesTotal,
                    videoCount,
                    followerDelta,
                    likesDelta,
                    videoDelta,
                    lastSnapshotDate
            ));
        }

        long safeTotalFollowers = totalFollowers;
        long safeTotalLikesTotal = totalLikesTotal;
        List<AnalyticsTikTokCreatorActivityRowResponse> rows = aggregates.stream()
                .sorted(Comparator.comparing(TikTokCreatorActivityAggregate::likesTotal, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TikTokCreatorActivityAggregate::followers, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TikTokCreatorActivityAggregate::accountName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(aggregate -> new AnalyticsTikTokCreatorActivityRowResponse(
                        aggregate.providerUserId(),
                        aggregate.accountName(),
                        aggregate.followers(),
                        aggregate.following(),
                        aggregate.likesTotal(),
                        aggregate.videoCount(),
                        aggregate.followerDelta(),
                        aggregate.likesDelta(),
                        aggregate.videoDelta(),
                        ratioPercent(aggregate.followers(), safeTotalFollowers),
                        ratioPercent(aggregate.likesTotal(), safeTotalLikesTotal),
                        aggregate.lastSnapshotDate()
                ))
                .toList();

        Map<LocalDate, List<AccountAnalyticsSnapshotEntity>> snapshotsByDate = snapshots.stream()
                .collect(Collectors.groupingBy(
                        AccountAnalyticsSnapshotEntity::getSnapshotDate,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<AnalyticsTikTokCreatorActivityTrendPointResponse> trend = snapshotsByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AnalyticsTikTokCreatorActivityTrendPointResponse(
                        entry.getKey(),
                        sumAccountMetric(entry.getValue(), AccountAnalyticsSnapshotEntity::getFollowers),
                        sumAccountMetric(entry.getValue(), AccountAnalyticsSnapshotEntity::getLikesTotal),
                        sumAccountMetric(entry.getValue(), AccountAnalyticsSnapshotEntity::getVideoCount)
                ))
                .toList();

        return new AnalyticsTikTokCreatorActivityResponse(
                slice.currentRangeLabel(),
                fromDate,
                toDate,
                tikTokAccountsById.size(),
                totalFollowers,
                totalFollowing,
                totalLikesTotal,
                totalVideoCount,
                totalFollowerDelta,
                totalLikesDelta,
                totalVideoDelta,
                trend,
                rows
        );
    }

    private AnalyticsForecastPredictionResponse buildNextPostForecast(List<AnalyticsPostRowResponse> eligibleRows,
                                                                      String metric,
                                                                      int minimumComparablePosts) {
        if (eligibleRows.size() < minimumComparablePosts) {
            return unavailableForecastPrediction(
                    "Need at least " + minimumComparablePosts + " comparable posts with live " + metricDefinition(metric).label().toLowerCase(Locale.ENGLISH) + " coverage to forecast the next post."
            );
        }

        double baselineValue = averageMetricValue(eligibleRows, metric);
        AnalyticsForecastRangeResponse range = buildForecastRange(eligibleRows, metric, baselineValue);

        return new AnalyticsForecastPredictionResponse(
                true,
                forecastConfidenceTier(eligibleRows, metric, baselineValue),
                eligibleRows.size(),
                range,
                String.format(
                        Locale.ENGLISH,
                        "%d comparable posts in this workspace slice averaged %.1f %s. The forecast range is built from slice-level variance, not a fixed multiplier.",
                        eligibleRows.size(),
                        baselineValue,
                        metricDefinition(metric).label().toLowerCase(Locale.ENGLISH)
                ),
                null
        );
    }

    private AnalyticsForecastBestSlotResponse buildNextBestSlotForecast(List<AnalyticsPostRowResponse> eligibleRows,
                                                                        String metric,
                                                                        int forecastDays,
                                                                        int minimumComparablePosts,
                                                                        int minimumSlotSampleSize) {
        if (eligibleRows.size() < minimumComparablePosts) {
            return unavailableForecastBestSlot(
                    "Need at least " + minimumComparablePosts + " comparable posts before recommending the next slot."
            );
        }

        double baselineValue = averageMetricValue(eligibleRows, metric);
        Map<ForecastSlotBucket, List<AnalyticsPostRowResponse>> rowsBySlot = new LinkedHashMap<>();
        for (AnalyticsPostRowResponse row : eligibleRows) {
            ForecastSlotBucket slotBucket = forecastSlotBucket(row);
            if (slotBucket == null) {
                continue;
            }
            rowsBySlot.computeIfAbsent(slotBucket, ignored -> new ArrayList<>()).add(row);
        }

        ForecastSlotCandidate candidate = rowsBySlot.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= minimumSlotSampleSize)
                .map(entry -> buildForecastSlotCandidate(entry.getKey(), entry.getValue(), metric, baselineValue))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(ForecastSlotCandidate::score).reversed()
                        .thenComparing(candidateItem -> candidateItem.bucket().label(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .findFirst()
                .orElse(null);

        if (candidate == null) {
            return unavailableForecastBestSlot(
                    "No weekday and hour bucket has enough fresh history to recommend a stronger publishing slot yet."
            );
        }

        OffsetDateTime predictedAt = nextUpcomingSlot(candidate.bucket(), forecastDays);
        if (predictedAt == null) {
            return unavailableForecastBestSlot(
                    "No historically strong slot falls inside the next " + forecastDays + " days."
            );
        }

        return new AnalyticsForecastBestSlotResponse(
                true,
                candidate.bucket().key(),
                candidate.bucket().label(),
                predictedAt,
                candidate.confidenceTier(),
                candidate.rows().size(),
                roundToSingleDecimal(baselineValue),
                roundToSingleDecimal(candidate.observedValue()),
                roundToSingleDecimal(candidate.liftPercent()),
                candidate.range(),
                String.format(
                        Locale.ENGLISH,
                        "%d comparable posts in %s averaged %.1f %s versus a slice baseline of %.1f, a %.1f%% lift.",
                        candidate.rows().size(),
                        candidate.bucket().label(),
                        candidate.observedValue(),
                        metricDefinition(metric).label().toLowerCase(Locale.ENGLISH),
                        baselineValue,
                        candidate.liftPercent()
                ),
                null
        );
    }

    private AnalyticsEndOfPeriodForecastResponse buildEndOfPeriodForecast(List<AnalyticsPostRowResponse> eligibleRows,
                                                                          String metric,
                                                                          int lookbackDays,
                                                                          int forecastDays,
                                                                          int plannedPosts,
                                                                          int minimumComparablePosts,
                                                                          AnalyticsForecastPredictionResponse nextPostForecast,
                                                                          AnalyticsForecastBestSlotResponse nextBestSlot) {
        if (eligibleRows.size() < minimumComparablePosts || !nextPostForecast.isAvailable() || nextPostForecast.getRange() == null) {
            return unavailableEndOfPeriodForecast(
                    forecastDays,
                    plannedPosts,
                    "Need at least " + minimumComparablePosts + " comparable posts before projecting a planning window."
            );
        }

        AnalyticsForecastRangeResponse baselineRange = nextPostForecast.getRange();
        double lowValue = safeForecastValue(baselineRange.getLowValue()) * plannedPosts;
        double expectedValue = safeForecastValue(baselineRange.getExpectedValue()) * plannedPosts;
        double highValue = safeForecastValue(baselineRange.getHighValue()) * plannedPosts;
        String basisSummary = String.format(
                Locale.ENGLISH,
                "Assumes %d posts in the next %d days at the slice baseline. Historical cadence in this slice is %.2f eligible posts/day.",
                plannedPosts,
                forecastDays,
                lookbackDays > 0 ? (double) eligibleRows.size() / lookbackDays : 0.0
        );
        String confidenceTier = nextPostForecast.getConfidenceTier();

        if (nextBestSlot != null && nextBestSlot.isAvailable() && nextBestSlot.getRange() != null && plannedPosts > 0) {
            lowValue = safeForecastValue(nextBestSlot.getRange().getLowValue())
                    + safeForecastValue(baselineRange.getLowValue()) * Math.max(plannedPosts - 1, 0);
            expectedValue = safeForecastValue(nextBestSlot.getRange().getExpectedValue())
                    + safeForecastValue(baselineRange.getExpectedValue()) * Math.max(plannedPosts - 1, 0);
            highValue = safeForecastValue(nextBestSlot.getRange().getHighValue())
                    + safeForecastValue(baselineRange.getHighValue()) * Math.max(plannedPosts - 1, 0);
            confidenceTier = lowerConfidenceTier(confidenceTier, nextBestSlot.getConfidenceTier());
            basisSummary = String.format(
                    Locale.ENGLISH,
                    "Assumes 1 post in %s and %d additional posts at the slice baseline in the next %d days. Historical cadence in this slice is %.2f eligible posts/day.",
                    nextBestSlot.getSlotLabel(),
                    Math.max(plannedPosts - 1, 0),
                    forecastDays,
                    lookbackDays > 0 ? (double) eligibleRows.size() / lookbackDays : 0.0
            );
        }

        return new AnalyticsEndOfPeriodForecastResponse(
                true,
                forecastDays,
                planningWindowLabel(forecastDays),
                plannedPosts,
                roundToTwoDecimals(lookbackDays > 0 ? (double) eligibleRows.size() / lookbackDays : 0.0),
                confidenceTier,
                eligibleRows.size(),
                new AnalyticsForecastRangeResponse(
                        roundToSingleDecimal(Math.max(0.0, lowValue)),
                        roundToSingleDecimal(Math.max(0.0, expectedValue)),
                        roundToSingleDecimal(Math.max(0.0, highValue))
                ),
                basisSummary,
                null
        );
    }

    private AnalyticsForecastPredictionResponse unavailableForecastPrediction(String reason) {
        return new AnalyticsForecastPredictionResponse(false, null, 0L, null, null, reason);
    }

    private AnalyticsForecastBestSlotResponse unavailableForecastBestSlot(String reason) {
        return new AnalyticsForecastBestSlotResponse(false, null, null, null, null, 0L, null, null, null, null, null, reason);
    }

    private AnalyticsEndOfPeriodForecastResponse unavailableEndOfPeriodForecast(int forecastDays,
                                                                                int plannedPosts,
                                                                                String reason) {
        return new AnalyticsEndOfPeriodForecastResponse(
                false,
                forecastDays,
                planningWindowLabel(forecastDays),
                plannedPosts,
                null,
                null,
                0L,
                null,
                null,
                reason
        );
    }

    private ForecastSlotCandidate buildForecastSlotCandidate(ForecastSlotBucket bucket,
                                                             List<AnalyticsPostRowResponse> rows,
                                                             String metric,
                                                             double baselineValue) {
        double observedValue = averageMetricValue(rows, metric);
        if (baselineValue <= 0.0 || observedValue <= baselineValue) {
            return null;
        }

        double liftPercent = ((observedValue - baselineValue) / baselineValue) * 100.0;
        double sampleWeight = Math.min(1.15, 0.55 + (rows.size() / 10.0));

        return new ForecastSlotCandidate(
                bucket,
                rows,
                observedValue,
                liftPercent,
                liftPercent * sampleWeight,
                buildForecastRange(rows, metric, observedValue),
                forecastConfidenceTier(rows, metric, observedValue)
        );
    }

    private ForecastSlotBucket forecastSlotBucket(AnalyticsPostRowResponse row) {
        if (row == null || row.getPublishedAt() == null) {
            return null;
        }

        OffsetDateTime publishedAt = row.getPublishedAt().withOffsetSameInstant(ZoneOffset.UTC);
        DayOfWeek dayOfWeek = publishedAt.getDayOfWeek();
        int bucketStartHour = (publishedAt.getHour() / 4) * 4;
        return new ForecastSlotBucket(
                dayOfWeek,
                bucketStartHour,
                dayOfWeek.name() + ":" + bucketStartHour,
                dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " · " + formatHourBucket(bucketStartHour)
        );
    }

    private OffsetDateTime nextUpcomingSlot(ForecastSlotBucket slotBucket, int forecastDays) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
        OffsetDateTime horizon = now.plusDays(forecastDays);

        for (int dayOffset = 0; dayOffset <= forecastDays; dayOffset++) {
            OffsetDateTime candidateDay = now.plusDays(dayOffset)
                    .withHour(slotBucket.startHour())
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
            if (!candidateDay.getDayOfWeek().equals(slotBucket.dayOfWeek())) {
                continue;
            }
            if (candidateDay.isBefore(now)) {
                continue;
            }
            if (candidateDay.isAfter(horizon)) {
                continue;
            }
            return candidateDay;
        }

        return null;
    }

    private AnalyticsForecastRangeResponse buildForecastRange(List<AnalyticsPostRowResponse> rows,
                                                              String metric,
                                                              double expectedValue) {
        if (rows.isEmpty()) {
            return new AnalyticsForecastRangeResponse(null, null, null);
        }

        double standardDeviation = metricStandardDeviation(rows, metric, expectedValue);
        double relativeVariance = expectedValue > 0.0 ? standardDeviation / expectedValue : 0.5;
        double marginRatio = rows.size() <= 1
                ? 0.3
                : Math.min(0.6, Math.max(0.12, (relativeVariance / Math.sqrt(rows.size())) * 1.96));

        double lowValue = Math.max(0.0, expectedValue * (1.0 - marginRatio));
        double highValue = Math.max(expectedValue, expectedValue * (1.0 + marginRatio));

        return new AnalyticsForecastRangeResponse(
                roundToSingleDecimal(lowValue),
                roundToSingleDecimal(Math.max(0.0, expectedValue)),
                roundToSingleDecimal(highValue)
        );
    }

    private double metricStandardDeviation(List<AnalyticsPostRowResponse> rows,
                                           String metric,
                                           double meanValue) {
        if (rows.size() <= 1) {
            return 0.0;
        }

        double variance = rows.stream()
                .mapToDouble(row -> {
                    double delta = patternMetricValue(row, metric) - meanValue;
                    return delta * delta;
                })
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    private String forecastConfidenceTier(List<AnalyticsPostRowResponse> rows,
                                          String metric,
                                          double expectedValue) {
        if (rows.isEmpty() || expectedValue <= 0.0) {
            return "LOW";
        }

        double coefficientOfVariation = metricStandardDeviation(rows, metric, expectedValue) / expectedValue;
        if (rows.size() >= 12 && coefficientOfVariation <= 0.7) {
            return "HIGH";
        }
        if (rows.size() >= 8 && coefficientOfVariation <= 1.2) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String lowerConfidenceTier(String left, String right) {
        int leftRank = confidenceTierRank(left);
        int rightRank = confidenceTierRank(right);
        return leftRank <= rightRank ? left : right;
    }

    private int confidenceTierRank(String confidenceTier) {
        if ("HIGH".equals(confidenceTier)) {
            return 3;
        }
        if ("MEDIUM".equals(confidenceTier)) {
            return 2;
        }
        return 1;
    }

    private boolean isForecastEligible(AnalyticsPostRowResponse row, String metric) {
        return isPatternEligible(row, metric);
    }

    private String normalizeForecastMetric(String metric) {
        return normalizeRankingMetric(metric);
    }

    private int normalizeForecastDays(int forecastDays) {
        return switch (forecastDays) {
            case 7, 14, 30 -> forecastDays;
            default -> forecastDays < 10 ? 7 : forecastDays < 22 ? 14 : 30;
        };
    }

    private int normalizePlannedPosts(int plannedPosts) {
        return Math.min(Math.max(plannedPosts, 1), 20);
    }

    private int minimumForecastComparablePosts() {
        return 8;
    }

    private int minimumForecastSlotSampleSize() {
        return 3;
    }

    private String planningWindowLabel(int forecastDays) {
        return "Next " + forecastDays + " days";
    }

    private double safeForecastValue(Double value) {
        return value != null ? value : 0.0;
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private List<AnalyticsPostMilestonePointResponse> buildMilestoneProgression(Long postId) {
        return postAnalyticsSnapshotRepo.findAllByPostId(postId).stream()
                .sorted(Comparator.comparing(PostAnalyticsSnapshotEntity::getFetchedAt))
                .map(this::toMilestonePoint)
                .toList();
    }

    private AnalyticsPostMilestonePointResponse toMilestonePoint(PostAnalyticsSnapshotEntity snapshot) {
        Long engagements = snapshot.getEngagements();
        if (engagements == null && hasAny(snapshot.getLikes(), snapshot.getComments(), snapshot.getShares())) {
            engagements = value(snapshot.getLikes()) + value(snapshot.getComments()) + value(snapshot.getShares());
        }

        Double engagementRate = snapshot.getEngagementRate();
        if (engagementRate == null && engagements != null && snapshot.getImpressions() != null && snapshot.getImpressions() > 0) {
            engagementRate = ((double) engagements / snapshot.getImpressions()) * 100.0;
        }

        return new AnalyticsPostMilestonePointResponse(
                snapshot.getFetchedAt(),
                snapshot.getImpressions(),
                snapshot.getReach(),
                snapshot.getLikes(),
                snapshot.getComments(),
                snapshot.getShares(),
                snapshot.getSaves(),
                snapshot.getClicks(),
                snapshot.getVideoViews(),
                snapshot.getWatchTimeMinutes(),
                engagements,
                engagementRate
        );
    }

    private PostComparableGroup resolvePostComparableGroup(List<AnalyticsPostRowResponse> rows,
                                                           AnalyticsPostRowResponse targetPost,
                                                           String metric) {
        List<AnalyticsPostRowResponse> mediaFormatGroup = rows.stream()
                .filter(row -> targetPost.getProvider().equals(row.getProvider()))
                .filter(row -> targetPost.getPostType().equals(row.getPostType()))
                .filter(row -> Objects.equals(targetPost.getMediaFormat(), row.getMediaFormat()))
                .filter(row -> hasRankingMetric(row, metric))
                .toList();
        if (mediaFormatGroup.size() >= 3) {
            return new PostComparableGroup(
                    labelForProvider(targetPost.getProvider()) + " " + labelForPostType(targetPost.getPostType()).toLowerCase(Locale.ENGLISH)
                            + " posts with " + labelForMediaFormat(targetPost.getMediaFormat()).toLowerCase(Locale.ENGLISH),
                    mediaFormatGroup
            );
        }

        List<AnalyticsPostRowResponse> postTypeGroup = rows.stream()
                .filter(row -> targetPost.getProvider().equals(row.getProvider()))
                .filter(row -> targetPost.getPostType().equals(row.getPostType()))
                .filter(row -> hasRankingMetric(row, metric))
                .toList();
        if (postTypeGroup.size() >= 3) {
            return new PostComparableGroup(
                    labelForProvider(targetPost.getProvider()) + " " + labelForPostType(targetPost.getPostType()).toLowerCase(Locale.ENGLISH) + " posts",
                    postTypeGroup
            );
        }

        List<AnalyticsPostRowResponse> providerGroup = rows.stream()
                .filter(row -> targetPost.getProvider().equals(row.getProvider()))
                .filter(row -> hasRankingMetric(row, metric))
                .toList();
        return new PostComparableGroup(labelForProvider(targetPost.getProvider()) + " posts", providerGroup);
    }

    private AnalyticsComparableBenchmarkResponse buildPostComparableBenchmark(AnalyticsPostRowResponse targetPost,
                                                                             PostComparableGroup comparableGroup,
                                                                             List<AnalyticsPostRowResponse> sliceRows,
                                                                             String metric) {
        List<Double> comparableValues = comparableGroup.rows().stream()
                .map(row -> metricValueOrNull(row, metric))
                .filter(Objects::nonNull)
                .toList();
        Double targetValue = metricValueOrNull(targetPost, metric);
        Double comparableAverageValue = averageDouble(comparableValues);
        Double sliceAverageValue = averageDouble(sliceRows.stream()
                .map(row -> metricValueOrNull(row, metric))
                .filter(Objects::nonNull)
                .toList());

        return new AnalyticsComparableBenchmarkResponse(
                comparableGroup.label(),
                metricDefinition(metric).label(),
                comparableValues.size(),
                targetValue,
                comparableAverageValue,
                sliceAverageValue,
                percentLift(targetValue, comparableAverageValue)
        );
    }

    private AnalyticsPercentileRankResponse buildPostPercentileRank(AnalyticsPostRowResponse targetPost,
                                                                    List<AnalyticsPostRowResponse> comparableRows,
                                                                    String metric) {
        Double targetValue = metricValueOrNull(targetPost, metric);
        List<Double> comparableValues = comparableRows.stream()
                .map(row -> metricValueOrNull(row, metric))
                .filter(Objects::nonNull)
                .toList();
        return buildPercentileRank(targetValue, comparableValues);
    }

    private AnalyticsComparableBenchmarkResponse buildGroupComparableBenchmark(String groupLabel,
                                                                              String basisLabel,
                                                                              AnalyticsBreakdownRowResponse targetRow,
                                                                              List<AnalyticsBreakdownRowResponse> comparableRows) {
        List<Double> comparableValues = comparableRows.stream()
                .map(AnalyticsBreakdownRowResponse::getAveragePerformancePerPost)
                .filter(Objects::nonNull)
                .toList();
        Double targetValue = targetRow.getAveragePerformancePerPost();
        Double comparableAverageValue = averageDouble(comparableValues);
        return new AnalyticsComparableBenchmarkResponse(
                groupLabel,
                basisLabel,
                comparableValues.size(),
                targetValue,
                comparableAverageValue,
                comparableAverageValue,
                percentLift(targetValue, comparableAverageValue)
        );
    }

    private AnalyticsPercentileRankResponse buildGroupPercentileRank(AnalyticsBreakdownRowResponse targetRow,
                                                                     List<AnalyticsBreakdownRowResponse> comparableRows) {
        List<Double> comparableValues = comparableRows.stream()
                .map(AnalyticsBreakdownRowResponse::getAveragePerformancePerPost)
                .filter(Objects::nonNull)
                .toList();
        return buildPercentileRank(targetRow.getAveragePerformancePerPost(), comparableValues);
    }

    private AnalyticsPercentileRankResponse buildPercentileRank(Double targetValue, List<Double> comparableValues) {
        if (targetValue == null || comparableValues.isEmpty()) {
            return new AnalyticsPercentileRankResponse(null, null, comparableValues.size());
        }

        long higherCount = comparableValues.stream()
                .filter(value -> value > targetValue)
                .count();
        int rank = (int) higherCount + 1;
        double percentile = comparableValues.size() <= 1
                ? 100.0
                : ((double) (comparableValues.size() - rank) / (comparableValues.size() - 1L)) * 100.0;

        return new AnalyticsPercentileRankResponse(roundToSingleDecimal(percentile), rank, comparableValues.size());
    }

    private AnalyticsDrilldownSummaryResponse toDrilldownSummary(AnalyticsBreakdownRowResponse row) {
        return new AnalyticsDrilldownSummaryResponse(
                row.getKey(),
                row.getLabel(),
                row.getPostsPublished(),
                row.getPerformanceValue(),
                row.getAveragePerformancePerPost(),
                row.getOutputSharePercent(),
                row.getPerformanceSharePercent(),
                row.getShareGapPercent()
        );
    }

    private AnalyticsDrilldownContributionResponse buildContributionSection(List<AnalyticsPostRowResponse> rows,
                                                                            String dimension,
                                                                            String metric) {
        long totalPostsPublished = rows.size();
        double totalPerformanceValue = computePerformanceValue(rows, metric);
        return new AnalyticsDrilldownContributionResponse(
                dimension,
                breakdownDimensionLabel(dimension),
                buildBreakdownRows(rows, dimension, metric, totalPostsPublished, totalPerformanceValue)
        );
    }

    private List<AnalyticsPostRowResponse> topPosts(List<AnalyticsPostRowResponse> rows, String metric) {
        return rows.stream()
                .filter(row -> hasRankingMetric(row, metric))
                .sorted(Comparator.comparingDouble(
                        (AnalyticsPostRowResponse row) -> metricValueOrZero(row, metric)
                ).reversed().thenComparing(
                        AnalyticsPostRowResponse::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(
                        AnalyticsPostRowResponse::getPostId,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .limit(5)
                .toList();
    }

    private AnalyticsBreakdownRowResponse findBreakdownRow(List<AnalyticsBreakdownRowResponse> rows,
                                                           String key,
                                                           String errorMessage) {
        return rows.stream()
                .filter(row -> key.equals(row.getKey()))
                .findFirst()
                .orElseThrow(() -> new SocialRavenException(errorMessage, HttpStatus.NOT_FOUND));
    }

    private Double averageDouble(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        return roundToSingleDecimal(values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0));
    }

    private Double metricValueOrNull(AnalyticsPostRowResponse row, String metric) {
        return hasRankingMetric(row, metric) ? patternMetricValue(row, metric) : null;
    }

    private double metricValueOrZero(AnalyticsPostRowResponse row, String metric) {
        Double value = metricValueOrNull(row, metric);
        return value != null ? value : 0.0;
    }

    private Double percentLift(Double targetValue, Double baselineValue) {
        if (targetValue == null || baselineValue == null || baselineValue <= 0.0) {
            return null;
        }
        return roundToSingleDecimal(((targetValue - baselineValue) / baselineValue) * 100.0);
    }

    private WorkspaceAnalyticsDataset loadDataset(WorkspaceAnalyticsSlice slice) {
        String workspaceId = requireWorkspaceId();
        List<PostEntity> analyticsPosts = postRepo.findAnalyticsPostsByWorkspaceId(workspaceId);
        Map<Long, WorkspacePostAnalyticsEntity> metricsByPostId = workspacePostAnalyticsRepo.findAllByWorkspaceId(workspaceId)
                .stream()
                .collect(Collectors.toMap(WorkspacePostAnalyticsEntity::getPostId, entity -> entity, (left, right) -> right));
        Map<String, ConnectedAccount> accountsByKey = accountProfileService.getAllConnectedAccounts(workspaceId, false)
                .stream()
                .collect(Collectors.toMap(this::accountKey, account -> account, (left, right) -> left, LinkedHashMap::new));

        List<AnalyticsPostRowResponse> currentRows = analyticsPosts.stream()
                .filter(slice::matchesCurrent)
                .map(post -> toRow(post, metricsByPostId.get(post.getId()), accountsByKey))
                .toList();

        List<AnalyticsPostRowResponse> previousRows = analyticsPosts.stream()
                .filter(slice::matchesPrevious)
                .map(post -> toRow(post, metricsByPostId.get(post.getId()), accountsByKey))
                .toList();

        return new WorkspaceAnalyticsDataset(currentRows, previousRows);
    }

    private AnalyticsPostRowResponse toRow(PostEntity post,
                                           WorkspacePostAnalyticsEntity metric,
                                           Map<String, ConnectedAccount> accountsByKey) {
        ConnectedAccount account = accountsByKey.get(accountKey(post.getProvider(), post.getProviderUserId()));
        String accountName = account != null && StringUtils.hasText(account.getUsername())
                ? account.getUsername()
                : post.getProviderUserId();
        PostCollectionEntity collection = post.getPostCollection();

        Long impressions = metric != null ? metric.getImpressions() : null;
        Long reach = metric != null ? metric.getReach() : null;
        Long likes = metric != null ? metric.getLikes() : null;
        Long comments = metric != null ? metric.getComments() : null;
        Long shares = metric != null ? metric.getShares() : null;
        Long saves = metric != null ? metric.getSaves() : null;
        Long clicks = metric != null ? metric.getClicks() : null;
        Long videoViews = metric != null ? metric.getVideoViews() : null;
        Long watchTimeMinutes = metric != null ? metric.getWatchTimeMinutes() : null;
        Long engagements = metric != null ? metric.getEngagements() : null;
        if (engagements == null && hasAny(likes, comments, shares)) {
            engagements = value(likes) + value(comments) + value(shares);
        }

        Double engagementRate = metric != null ? metric.getEngagementRate() : null;
        if (engagementRate == null && engagements != null && impressions != null && impressions > 0) {
            engagementRate = ((double) engagements / impressions) * 100.0;
        }

        return new AnalyticsPostRowResponse(
                post.getId(),
                post.getProvider().name(),
                post.getProviderUserId(),
                post.getProviderPostId(),
                accountName,
                collection != null ? collection.getId() : null,
                collection != null ? labelForCampaign(collection) : null,
                collection != null ? collection.getDescription() : null,
                post.getPostType().name(),
                resolveMediaFormat(post),
                metric != null && metric.getFreshnessStatus() != null
                        ? metric.getFreshnessStatus().name()
                        : AnalyticsFreshnessStatus.NO_DATA.name(),
                metric != null && metric.getPublishedAt() != null ? metric.getPublishedAt() : post.getScheduledTime(),
                metric != null ? metric.getLastCollectedAt() : null,
                metric != null,
                buildMetricAvailability(post, metric),
                impressions,
                reach,
                likes,
                comments,
                shares,
                saves,
                clicks,
                videoViews,
                watchTimeMinutes,
                engagements,
                engagementRate
        );
    }

    private String resolveMediaFormat(PostEntity post) {
        PostCollectionEntity collection = post.getPostCollection();
        if (collection == null || collection.getMediaFiles() == null || collection.getMediaFiles().isEmpty()) {
            return "TEXT_ONLY";
        }

        boolean hasImage = false;
        boolean hasVideo = false;
        boolean hasOther = false;

        for (var mediaFile : collection.getMediaFiles()) {
            if (!StringUtils.hasText(mediaFile.getMimeType())) {
                hasOther = true;
                continue;
            }

            String mimeType = mediaFile.getMimeType().toLowerCase(Locale.ENGLISH);
            if (mimeType.startsWith("image/")) {
                hasImage = true;
            } else if (mimeType.startsWith("video/")) {
                hasVideo = true;
            } else {
                hasOther = true;
            }
        }

        if (hasImage && hasVideo) {
            return "MIXED_MEDIA";
        }
        if (hasVideo) {
            return "VIDEO_ASSET";
        }
        if (hasImage) {
            return "IMAGE_ASSET";
        }
        return hasOther ? "OTHER_ASSET" : "TEXT_ONLY";
    }

    private List<AnalyticsMetricAvailabilityWindow> buildMetricAvailability(PostEntity post,
                                                                           WorkspacePostAnalyticsEntity metric) {
        if (metric != null && metric.getMetricAvailability() != null && !metric.getMetricAvailability().isEmpty()) {
            return metric.getMetricAvailability();
        }

        if (!Provider.X.equals(post.getProvider())) {
            return List.of();
        }

        List<AnalyticsMetricAvailabilityWindow> availability = new ArrayList<>();
        OffsetDateTime publishedAt = post.getScheduledTime();
        OffsetDateTime privateWindowEndsAt = publishedAt != null ? publishedAt.plusDays(30) : null;
        boolean insidePrivateWindow = privateWindowEndsAt == null
                || !OffsetDateTime.now(ZoneOffset.UTC).isAfter(privateWindowEndsAt);

        availability.add(AnalyticsMetricAvailabilityWindow.builder()
                .metrics(List.of("IMPRESSIONS", "LIKES", "COMMENTS", "SHARES"))
                .label("Public post metrics")
                .status("AVAILABLE")
                .source("PUBLIC")
                .windowStartsAt(publishedAt)
                .note("Collected from X public post metrics.")
                .build());

        availability.add(AnalyticsMetricAvailabilityWindow.builder()
                .metrics(List.of("CLICKS"))
                .label("User-context click metrics")
                .status(insidePrivateWindow ? "AVAILABLE" : "WINDOW_EXPIRED")
                .source("USER_CONTEXT")
                .windowStartsAt(publishedAt)
                .windowEndsAt(privateWindowEndsAt)
                .note(
                        insidePrivateWindow
                                ? "Owned X posts can still collect private click metrics."
                                : "Owned X post private click metrics are outside X's 30-day collection window."
                )
                .build());

        if (PostType.VIDEO.equals(post.getPostType())) {
            availability.add(AnalyticsMetricAvailabilityWindow.builder()
                    .metrics(List.of("VIDEO_VIEWS"))
                    .label("Video media metrics")
                    .status("AVAILABLE")
                    .source("MEDIA_PUBLIC")
                    .windowStartsAt(publishedAt)
                    .note("Collected from X media metrics for video posts.")
                    .build());
        }

        return availability;
    }

    private OverviewAggregate aggregate(List<AnalyticsPostRowResponse> rows) {
        long impressions = sumLong(rows, AnalyticsPostRowResponse::getImpressions);
        long reach = sumLong(rows, AnalyticsPostRowResponse::getReach);
        long clicks = sumLong(rows, AnalyticsPostRowResponse::getClicks);
        long videoViews = sumLong(rows, AnalyticsPostRowResponse::getVideoViews);
        long watchTimeMinutes = sumLong(rows, AnalyticsPostRowResponse::getWatchTimeMinutes);
        long engagements = sumLong(rows, AnalyticsPostRowResponse::getEngagements);
        double engagementRate = impressions > 0 ? ((double) engagements / impressions) * 100.0 : 0.0;
        return new OverviewAggregate(impressions, reach, engagements, engagementRate, clicks, videoViews, watchTimeMinutes, rows.size());
    }

    private List<AnalyticsTrendExplorerPointResponse> buildTrendPoints(List<AnalyticsPostRowResponse> rows,
                                                                       String metric,
                                                                       LocalDate fromDate,
                                                                       LocalDate toDate,
                                                                       int bucketSizeDays) {
        List<AnalyticsTrendExplorerPointResponse> points = new ArrayList<>();
        LocalDate cursor = fromDate;

        while (!cursor.isAfter(toDate)) {
            LocalDate bucketStartDate = cursor;
            LocalDate bucketEndDate = bucketStartDate.plusDays(bucketSizeDays - 1L);
            if (bucketEndDate.isAfter(toDate)) {
                bucketEndDate = toDate;
            }

            LocalDate safeBucketEndDate = bucketEndDate;
            List<AnalyticsPostRowResponse> bucketRows = rows.stream()
                    .filter(row -> fallsInsideBucket(row, bucketStartDate, safeBucketEndDate))
                    .toList();
            double performanceValue = computePerformanceValue(bucketRows, metric);
            long postsPublished = bucketRows.size();

            points.add(new AnalyticsTrendExplorerPointResponse(
                    bucketStartDate.toString(),
                    bucketStartDate,
                    safeBucketEndDate,
                    performanceValue,
                    postsPublished,
                    postsPublished > 0 ? performanceValue / postsPublished : null
            ));

            cursor = safeBucketEndDate.plusDays(1);
        }

        return points;
    }

    private boolean fallsInsideBucket(AnalyticsPostRowResponse row, LocalDate fromDate, LocalDate toDate) {
        if (row.getPublishedAt() == null) {
            return false;
        }
        LocalDate publishedDate = row.getPublishedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        return !publishedDate.isBefore(fromDate) && !publishedDate.isAfter(toDate);
    }

    private List<AnalyticsBreakdownRowResponse> buildBreakdownRows(List<AnalyticsPostRowResponse> rows,
                                                                   String dimension,
                                                                   String metric,
                                                                   long totalPostsPublished,
                                                                   double totalPerformanceValue) {
        Map<String, BreakdownAccumulator> accumulators = new LinkedHashMap<>();
        if ("weekday".equals(dimension)) {
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                accumulators.put(
                        dayOfWeek.name(),
                        new BreakdownAccumulator(
                                dayOfWeek.name(),
                                dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                                dayOfWeek.getValue()
                        )
                );
            }
        } else if ("hourBucket".equals(dimension)) {
            for (int startHour = 0; startHour < 24; startHour += 4) {
                accumulators.put(
                        String.format(Locale.ENGLISH, "%02d", startHour),
                        new BreakdownAccumulator(
                                String.format(Locale.ENGLISH, "%02d", startHour),
                                formatHourBucket(startHour),
                                startHour
                        )
                );
            }
        }

        for (AnalyticsPostRowResponse row : rows) {
            BreakdownKey key = breakdownKeyForRow(row, dimension);
            if (key == null) {
                continue;
            }

            BreakdownAccumulator accumulator = accumulators.computeIfAbsent(
                    key.key(),
                    ignored -> new BreakdownAccumulator(key.key(), key.label(), key.sortOrder())
            );
            accumulator.increment(computeRowMetricValue(row, metric));
        }

        Comparator<BreakdownAccumulator> comparator =
                "weekday".equals(dimension) || "hourBucket".equals(dimension)
                        ? Comparator.comparing(BreakdownAccumulator::sortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        : Comparator.comparingDouble(BreakdownAccumulator::performanceValue).reversed()
                                .thenComparing(Comparator.comparingLong(BreakdownAccumulator::postsPublished).reversed())
                                .thenComparing(BreakdownAccumulator::label, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

        return accumulators.values().stream()
                .filter(accumulator ->
                        accumulator.postsPublished() > 0
                                || "weekday".equals(dimension)
                                || "hourBucket".equals(dimension)
                )
                .sorted(comparator)
                .map(accumulator -> {
                    Double outputSharePercent = ratioPercent(accumulator.postsPublished(), totalPostsPublished);
                    Double performanceSharePercent = ratioPercent(accumulator.performanceValue(), totalPerformanceValue);
                    return new AnalyticsBreakdownRowResponse(
                            accumulator.key(),
                            accumulator.label(),
                            accumulator.postsPublished(),
                            accumulator.performanceValue(),
                            outputSharePercent,
                            performanceSharePercent,
                            outputSharePercent != null && performanceSharePercent != null
                                    ? performanceSharePercent - outputSharePercent
                                    : null,
                            accumulator.postsPublished() > 0
                                    ? accumulator.performanceValue() / accumulator.postsPublished()
                                    : null
                    );
                })
                .toList();
    }

    private List<AnalyticsPatternContextResponse> buildPatternContexts(List<AnalyticsPostRowResponse> rows,
                                                                       String scope,
                                                                       String metric,
                                                                       int minimumSampleSize) {
        Map<PatternContextKey, List<AnalyticsPostRowResponse>> rowsByContext = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> patternContextKeyForRow(row, scope),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return rowsByContext.entrySet().stream()
                .sorted(Comparator.comparing(
                        (Map.Entry<PatternContextKey, List<AnalyticsPostRowResponse>> entry) -> entry.getKey().sortOrder(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).thenComparing(entry -> entry.getKey().label(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(entry -> buildPatternContext(entry.getKey(), entry.getValue(), scope, metric, minimumSampleSize))
                .toList();
    }

    private List<AnalyticsRecommendationEntity> regenerateRecommendationsIfNeeded(WorkspaceAnalyticsSlice slice,
                                                                                  List<AnalyticsPostRowResponse> currentRows,
                                                                                  String scope,
                                                                                  String metric) {
        String workspaceId = requireWorkspaceId();
        String sliceKey = buildRecommendationSliceKey(slice, scope, metric);
        List<RecommendationCandidate> candidates = deriveRecommendations(slice, currentRows, scope, metric);
        String sliceFingerprint = buildRecommendationFingerprint(candidates);

        List<AnalyticsRecommendationEntity> existingRecommendations =
                analyticsRecommendationRepo.findAllByWorkspaceIdAndSliceKeyOrderByExpectedImpactScoreDesc(workspaceId, sliceKey);

        boolean requiresRefresh = existingRecommendations.isEmpty()
                || existingRecommendations.stream()
                .anyMatch(recommendation -> !sliceFingerprint.equals(recommendation.getSliceFingerprint()));

        if (!requiresRefresh) {
            return existingRecommendations;
        }

        Map<String, AnalyticsRecommendationEntity> existingByKey = existingRecommendations.stream()
                .collect(Collectors.toMap(
                        AnalyticsRecommendationEntity::getRecommendationKey,
                        recommendation -> recommendation,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<String> activeKeys = new HashSet<>();
        List<AnalyticsRecommendationEntity> entitiesToSave = new ArrayList<>();

        for (RecommendationCandidate candidate : candidates) {
            AnalyticsRecommendationEntity entity = existingByKey.getOrDefault(
                    candidate.recommendationKey(),
                    new AnalyticsRecommendationEntity()
            );

            entity.setWorkspaceId(workspaceId);
            entity.setSliceKey(sliceKey);
            entity.setSliceFingerprint(sliceFingerprint);
            entity.setRecommendationKey(candidate.recommendationKey());
            entity.setScope(scope);
            entity.setMetric(metric);
            entity.setSourceType(candidate.sourceType());
            entity.setContextLabel(candidate.contextLabel());
            entity.setTitle(candidate.title());
            entity.setActionSummary(candidate.actionSummary());
            entity.setEvidenceSummary(candidate.evidenceSummary());
            entity.setConfidenceTier(candidate.confidenceTier());
            entity.setPriority(candidate.priority());
            entity.setExpectedImpactScore(candidate.expectedImpactScore());
            entity.setTimeWindowStartAt(candidate.timeWindowStartAt());
            entity.setTimeWindowEndAt(candidate.timeWindowEndAt());
            entity.setActive(true);

            if (entity.getDismissedAt() != null && !recommendationDismissible(candidate.priority())) {
                entity.setDismissedAt(null);
            }

            entitiesToSave.add(entity);
            activeKeys.add(candidate.recommendationKey());
        }

        for (AnalyticsRecommendationEntity existingRecommendation : existingRecommendations) {
            if (activeKeys.contains(existingRecommendation.getRecommendationKey())) {
                continue;
            }
            existingRecommendation.setSliceFingerprint(sliceFingerprint);
            existingRecommendation.setActive(false);
            entitiesToSave.add(existingRecommendation);
        }

        if (entitiesToSave.isEmpty()) {
            return List.of();
        }

        analyticsRecommendationRepo.saveAll(entitiesToSave);
        return analyticsRecommendationRepo.findAllByWorkspaceIdAndSliceKeyOrderByExpectedImpactScoreDesc(workspaceId, sliceKey);
    }

    private List<RecommendationCandidate> deriveRecommendations(WorkspaceAnalyticsSlice slice,
                                                                List<AnalyticsPostRowResponse> currentRows,
                                                                String scope,
                                                                String metric) {
        if (currentRows.isEmpty()) {
            return List.of();
        }

        int minimumSampleSize = minimumPatternSampleSize();
        List<AnalyticsPatternContextResponse> contexts = buildPatternContexts(currentRows, scope, metric, minimumSampleSize);
        long totalPostsPublished = currentRows.size();
        double totalPerformanceValue = computePerformanceValue(currentRows, metric);

        Map<String, AnalyticsBreakdownRowResponse> accountBreakdowns = rowsByKey(
                buildBreakdownRows(currentRows, "account", metric, totalPostsPublished, totalPerformanceValue)
        );
        Map<String, AnalyticsBreakdownRowResponse> platformBreakdowns = rowsByKey(
                buildBreakdownRows(currentRows, "platform", metric, totalPostsPublished, totalPerformanceValue)
        );
        Map<String, AnalyticsBreakdownRowResponse> postTypeBreakdowns = rowsByKey(
                buildBreakdownRows(currentRows, "postType", metric, totalPostsPublished, totalPerformanceValue)
        );
        Map<String, AnalyticsBreakdownRowResponse> mediaFormatBreakdowns = rowsByKey(
                buildBreakdownRows(currentRows, "mediaFormat", metric, totalPostsPublished, totalPerformanceValue)
        );

        Map<String, RecommendationCandidate> candidatesByKey = new LinkedHashMap<>();
        for (AnalyticsPatternContextResponse context : contexts) {
            AnalyticsPatternResponse topPostingWindowPattern = topPattern(context.getPostingWindowPatterns());
            addRecommendationCandidate(
                    candidatesByKey,
                    buildPostingWindowRecommendation(slice, context, topPostingWindowPattern, metric)
            );

            AnalyticsPatternResponse topFormatPattern = topPattern(context.getFormatPatterns());
            addRecommendationCandidate(
                    candidatesByKey,
                    buildFormatRecommendation(
                            slice,
                            context,
                            topFormatPattern,
                            metric,
                            postTypeBreakdowns,
                            mediaFormatBreakdowns
                    )
            );

            if (!"account".equals(scope)) {
                AnalyticsPatternResponse topAccountPattern = topPattern(context.getAccountPatterns());
                addRecommendationCandidate(
                        candidatesByKey,
                        buildAccountRecommendation(
                                slice,
                                context,
                                topAccountPattern,
                                metric,
                                accountBreakdowns
                        )
                );
            }
        }

        addRecommendationCandidate(
                candidatesByKey,
                buildNegativeContributionRecommendation(
                        slice,
                        metric,
                        scope,
                        accountBreakdowns.values(),
                        platformBreakdowns.values(),
                        postTypeBreakdowns.values(),
                        mediaFormatBreakdowns.values()
                )
        );

        return candidatesByKey.values().stream()
                .sorted(Comparator.comparingDouble(RecommendationCandidate::expectedImpactScore).reversed()
                        .thenComparing(RecommendationCandidate::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .limit(6)
                .toList();
    }

    private Map<String, AnalyticsBreakdownRowResponse> rowsByKey(List<AnalyticsBreakdownRowResponse> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        AnalyticsBreakdownRowResponse::getKey,
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private AnalyticsPatternResponse topPattern(List<AnalyticsPatternResponse> patterns) {
        return patterns.stream()
                .sorted(patternComparator())
                .findFirst()
                .orElse(null);
    }

    private void addRecommendationCandidate(Map<String, RecommendationCandidate> candidatesByKey,
                                            RecommendationCandidate candidate) {
        if (candidate == null) {
            return;
        }

        RecommendationCandidate existing = candidatesByKey.get(candidate.recommendationKey());
        if (existing == null || candidate.expectedImpactScore() > existing.expectedImpactScore()) {
            candidatesByKey.put(candidate.recommendationKey(), candidate);
        }
    }

    private RecommendationCandidate buildPostingWindowRecommendation(WorkspaceAnalyticsSlice slice,
                                                                     AnalyticsPatternContextResponse context,
                                                                     AnalyticsPatternResponse pattern,
                                                                     String metric) {
        if (pattern == null) {
            return null;
        }

        String title = "weekday".equals(pattern.getDimension())
                ? "Lean into " + pattern.getLabel() + " publishing"
                : "Publish more in " + pattern.getLabel();
        String actionSummary = String.format(
                Locale.ENGLISH,
                "%s increase publishing volume in %s where %s is already outperforming the %s baseline.",
                contextActionPrefix(context),
                pattern.getLabel(),
                metricDefinition(metric).label().toLowerCase(Locale.ENGLISH),
                context.getContextLabel().toLowerCase(Locale.ENGLISH)
        );

        return recommendationCandidate(
                recommendationKey("POSTING_WINDOW_PATTERN", context.getContextKey(), pattern.getDimension(), pattern.getKey(), metric),
                "POSTING_WINDOW_PATTERN",
                context.getContextLabel(),
                title,
                actionSummary,
                pattern.getEvidenceSummary(),
                pattern.getConfidenceTier(),
                patternImpactScore(pattern),
                slice.currentStartAt(),
                slice.currentEndAt()
        );
    }

    private RecommendationCandidate buildFormatRecommendation(WorkspaceAnalyticsSlice slice,
                                                              AnalyticsPatternContextResponse context,
                                                              AnalyticsPatternResponse pattern,
                                                              String metric,
                                                              Map<String, AnalyticsBreakdownRowResponse> postTypeBreakdowns,
                                                              Map<String, AnalyticsBreakdownRowResponse> mediaFormatBreakdowns) {
        if (pattern == null) {
            return null;
        }

        AnalyticsBreakdownRowResponse contributionRow = "postType".equals(pattern.getDimension())
                ? postTypeBreakdowns.get(pattern.getKey())
                : mediaFormatBreakdowns.get(pattern.getKey());

        if (contributionRow != null && contributionRow.getShareGapPercent() != null && contributionRow.getShareGapPercent() < -5.0) {
            return null;
        }

        double score = patternImpactScore(pattern);
        String evidenceSummary = pattern.getEvidenceSummary();
        if (contributionRow != null && contributionRow.getShareGapPercent() != null && contributionRow.getShareGapPercent() > 0.0) {
            score = Math.max(score, (score + contributionImpactScore(contributionRow)) / 2.0);
            evidenceSummary = pattern.getEvidenceSummary() + " " + contributionEvidenceSummary(contributionRow, metric);
        }

        String title = "Increase " + pattern.getLabel().toLowerCase(Locale.ENGLISH) + " output";
        String actionSummary = String.format(
                Locale.ENGLISH,
                "%s scale %s content more aggressively because it is outperforming the %s baseline.",
                contextActionPrefix(context),
                pattern.getLabel().toLowerCase(Locale.ENGLISH),
                context.getContextLabel().toLowerCase(Locale.ENGLISH)
        );

        return recommendationCandidate(
                recommendationKey("FORMAT_PATTERN", context.getContextKey(), pattern.getDimension(), pattern.getKey(), metric),
                "FORMAT_PATTERN",
                context.getContextLabel(),
                title,
                actionSummary,
                evidenceSummary,
                pattern.getConfidenceTier(),
                score,
                slice.currentStartAt(),
                slice.currentEndAt()
        );
    }

    private RecommendationCandidate buildAccountRecommendation(WorkspaceAnalyticsSlice slice,
                                                               AnalyticsPatternContextResponse context,
                                                               AnalyticsPatternResponse pattern,
                                                               String metric,
                                                               Map<String, AnalyticsBreakdownRowResponse> accountBreakdowns) {
        if (pattern == null) {
            return null;
        }

        AnalyticsBreakdownRowResponse contributionRow = accountBreakdowns.get(pattern.getKey());
        if (contributionRow != null && contributionRow.getShareGapPercent() != null && contributionRow.getShareGapPercent() < 0.0) {
            return null;
        }

        double score = patternImpactScore(pattern);
        String evidenceSummary = pattern.getEvidenceSummary();
        if (contributionRow != null && contributionRow.getShareGapPercent() != null && contributionRow.getShareGapPercent() > 0.0) {
            score = Math.max(score, contributionImpactScore(contributionRow));
            evidenceSummary = pattern.getEvidenceSummary() + " " + contributionEvidenceSummary(contributionRow, metric);
        }

        String title = "Shift more volume to " + pattern.getLabel();
        String actionSummary = String.format(
                Locale.ENGLISH,
                "%s give %s a bigger share of the next publishing cycle because it is converting a smaller share of output into stronger %s.",
                contextActionPrefix(context),
                pattern.getLabel(),
                metricDefinition(metric).label().toLowerCase(Locale.ENGLISH)
        );

        return recommendationCandidate(
                recommendationKey("ACCOUNT_PATTERN", context.getContextKey(), pattern.getDimension(), pattern.getKey(), metric),
                "ACCOUNT_PATTERN",
                context.getContextLabel(),
                title,
                actionSummary,
                evidenceSummary,
                pattern.getConfidenceTier(),
                score,
                slice.currentStartAt(),
                slice.currentEndAt()
        );
    }

    private RecommendationCandidate buildNegativeContributionRecommendation(WorkspaceAnalyticsSlice slice,
                                                                            String metric,
                                                                            String scope,
                                                                            java.util.Collection<AnalyticsBreakdownRowResponse> accountRows,
                                                                            java.util.Collection<AnalyticsBreakdownRowResponse> platformRows,
                                                                            java.util.Collection<AnalyticsBreakdownRowResponse> postTypeRows,
                                                                            java.util.Collection<AnalyticsBreakdownRowResponse> mediaFormatRows) {
        List<ContributionTarget> candidates = new ArrayList<>();
        if (!"account".equals(scope)) {
            candidates.addAll(accountRows.stream()
                    .map(row -> new ContributionTarget("account", row))
                    .toList());
        }
        if (slice.provider() == null && !"platform".equals(scope)) {
            candidates.addAll(platformRows.stream()
                    .map(row -> new ContributionTarget("platform", row))
                    .toList());
        }
        candidates.addAll(postTypeRows.stream()
                .map(row -> new ContributionTarget("postType", row))
                .toList());
        candidates.addAll(mediaFormatRows.stream()
                .map(row -> new ContributionTarget("mediaFormat", row))
                .toList());

        ContributionTarget target = candidates.stream()
                .filter(candidate -> candidate.row().getShareGapPercent() != null)
                .filter(candidate -> candidate.row().getShareGapPercent() <= -10.0)
                .filter(candidate -> candidate.row().getPostsPublished() >= 2L)
                .sorted(Comparator.comparingDouble(
                        (ContributionTarget candidate) -> candidate.row().getShareGapPercent()
                ).thenComparing(Comparator.comparingLong(
                        (ContributionTarget candidate) -> candidate.row().getPostsPublished()
                ).reversed()))
                .findFirst()
                .orElse(null);

        if (target == null) {
            return null;
        }

        AnalyticsBreakdownRowResponse row = target.row();
        String title = switch (target.dimension()) {
            case "account" -> "Fix or reduce output from " + row.getLabel();
            case "platform" -> "Fix " + row.getLabel() + " before scaling";
            case "postType" -> "Rework " + row.getLabel().toLowerCase(Locale.ENGLISH) + " posts before scaling";
            default -> "Rework " + row.getLabel().toLowerCase(Locale.ENGLISH) + " content before scaling";
        };
        String actionSummary = String.format(
                Locale.ENGLISH,
                "%s is taking more of the publishing volume than it is returning in %s. Fix the creative or reduce its share before adding more output.",
                row.getLabel(),
                metricDefinition(metric).label().toLowerCase(Locale.ENGLISH)
        );

        return recommendationCandidate(
                recommendationKey("CONTRIBUTION_GAP", target.dimension(), row.getKey(), metric),
                "CONTRIBUTION_GAP",
                breakdownDimensionLabel(target.dimension()),
                title,
                actionSummary,
                contributionEvidenceSummary(row, metric),
                contributionConfidenceTier(row.getPostsPublished(), Math.abs(row.getShareGapPercent())),
                contributionImpactScore(row),
                slice.currentStartAt(),
                slice.currentEndAt()
        );
    }

    private RecommendationCandidate recommendationCandidate(String recommendationKey,
                                                            String sourceType,
                                                            String contextLabel,
                                                            String title,
                                                            String actionSummary,
                                                            String evidenceSummary,
                                                            String confidenceTier,
                                                            double expectedImpactScore,
                                                            OffsetDateTime timeWindowStartAt,
                                                            OffsetDateTime timeWindowEndAt) {
        double normalizedScore = roundToSingleDecimal(expectedImpactScore);
        return new RecommendationCandidate(
                recommendationKey,
                sourceType,
                contextLabel,
                title,
                actionSummary,
                evidenceSummary,
                confidenceTier,
                recommendationPriority(normalizedScore),
                normalizedScore,
                timeWindowStartAt,
                timeWindowEndAt
        );
    }

    private double patternImpactScore(AnalyticsPatternResponse pattern) {
        double confidenceWeight = switch (pattern.getConfidenceTier()) {
            case "HIGH" -> 1.0;
            case "MEDIUM" -> 0.82;
            default -> 0.64;
        };
        double sampleWeight = Math.min(1.15, 0.55 + (pattern.getSampleSize() / 10.0));
        return pattern.getLiftPercent() * confidenceWeight * sampleWeight;
    }

    private double contributionImpactScore(AnalyticsBreakdownRowResponse row) {
        double shareGap = Math.abs(row.getShareGapPercent() != null ? row.getShareGapPercent() : 0.0);
        double postWeight = Math.min(1.1, 0.5 + (row.getPostsPublished() / 8.0));
        return shareGap * postWeight * 1.6;
    }

    private String contributionEvidenceSummary(AnalyticsBreakdownRowResponse row, String metric) {
        return String.format(
                Locale.ENGLISH,
                "%s delivered %s of %s from %s of posts, a %+.1f pt share gap across %d posts.",
                row.getLabel(),
                formatPercentValue(row.getPerformanceSharePercent()),
                metricDefinition(metric).label().toLowerCase(Locale.ENGLISH),
                formatPercentValue(row.getOutputSharePercent()),
                row.getShareGapPercent(),
                row.getPostsPublished()
        );
    }

    private String contributionConfidenceTier(long postsPublished, double shareGapAbs) {
        if (postsPublished >= 5 && shareGapAbs >= 20.0) {
            return "HIGH";
        }
        if (postsPublished >= 3 && shareGapAbs >= 12.0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String recommendationPriority(double expectedImpactScore) {
        if (expectedImpactScore >= 75.0) {
            return "HIGH";
        }
        if (expectedImpactScore >= 35.0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean recommendationDismissible(String priority) {
        return "LOW".equalsIgnoreCase(priority);
    }

    private AnalyticsRecommendationResponse toRecommendationResponse(AnalyticsRecommendationEntity entity) {
        return new AnalyticsRecommendationResponse(
                entity.getId(),
                entity.getRecommendationKey(),
                entity.getSourceType(),
                entity.getContextLabel(),
                entity.getTitle(),
                entity.getActionSummary(),
                entity.getEvidenceSummary(),
                entity.getConfidenceTier(),
                entity.getPriority(),
                entity.getExpectedImpactScore(),
                entity.getTimeWindowStartAt(),
                entity.getTimeWindowEndAt(),
                recommendationDismissible(entity.getPriority())
        );
    }

    private String buildRecommendationSliceKey(WorkspaceAnalyticsSlice slice, String scope, String metric) {
        return String.format(
                Locale.ENGLISH,
                "days=%d|provider=%s|account=%s|campaign=%s|postType=%s|scope=%s|metric=%s",
                slice.days(),
                slice.provider() != null ? slice.provider().name() : "ALL",
                StringUtils.hasText(slice.providerUserId()) ? slice.providerUserId() : "ALL",
                slice.campaignId() != null ? slice.campaignId() : "ALL",
                slice.postType() != null ? slice.postType().name() : "ALL",
                scope,
                metric
        );
    }

    private String buildRecommendationFingerprint(List<RecommendationCandidate> candidates) {
        String rawFingerprint = candidates.stream()
                .sorted(Comparator.comparing(RecommendationCandidate::recommendationKey))
                .map(candidate -> String.join(
                        "|",
                        candidate.recommendationKey(),
                        safeFingerprintValue(candidate.sourceType()),
                        safeFingerprintValue(candidate.contextLabel()),
                        safeFingerprintValue(candidate.title()),
                        safeFingerprintValue(candidate.actionSummary()),
                        safeFingerprintValue(candidate.evidenceSummary()),
                        safeFingerprintValue(candidate.confidenceTier()),
                        safeFingerprintValue(candidate.priority()),
                        String.format(Locale.ENGLISH, "%.1f", candidate.expectedImpactScore()),
                        safeFingerprintValue(candidate.timeWindowStartAt() != null ? candidate.timeWindowStartAt().toString() : null),
                        safeFingerprintValue(candidate.timeWindowEndAt() != null ? candidate.timeWindowEndAt().toString() : null)
                ))
                .collect(Collectors.joining("\n"));
        return sha256Hex(rawFingerprint);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String recommendationKey(String sourceType,
                                     String firstKeyPart,
                                     String secondKeyPart,
                                     String metric) {
        return recommendationKey(sourceType, firstKeyPart, secondKeyPart, null, metric);
    }

    private String recommendationKey(String sourceType,
                                     String firstKeyPart,
                                     String secondKeyPart,
                                     String thirdKeyPart,
                                     String metric) {
        return String.join(
                "|",
                safeFingerprintValue(sourceType),
                safeFingerprintValue(firstKeyPart),
                safeFingerprintValue(secondKeyPart),
                safeFingerprintValue(thirdKeyPart),
                safeFingerprintValue(metric)
        );
    }

    private String contextActionPrefix(AnalyticsPatternContextResponse context) {
        return "Workspace".equals(context.getContextLabel())
                ? "Across this slice,"
                : "Inside " + context.getContextLabel() + ",";
    }

    private String formatPercentValue(Double value) {
        if (value == null) {
            return "—";
        }
        return String.format(Locale.ENGLISH, "%.1f%%", value);
    }

    private String safeFingerprintValue(String value) {
        return StringUtils.hasText(value) ? value : "NONE";
    }

    private double roundToSingleDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private AnalyticsPatternContextResponse buildPatternContext(PatternContextKey contextKey,
                                                                List<AnalyticsPostRowResponse> rawRows,
                                                                String scope,
                                                                String metric,
                                                                int minimumSampleSize) {
        List<AnalyticsPostRowResponse> eligibleRows = rawRows.stream()
                .filter(row -> isPatternEligible(row, metric))
                .toList();
        double baselineValue = averageMetricValue(eligibleRows, metric);

        return new AnalyticsPatternContextResponse(
                contextKey.key(),
                contextKey.label(),
                baselineValue,
                eligibleRows.size(),
                Math.max(rawRows.size() - eligibleRows.size(), 0),
                buildPostingWindowPatterns(eligibleRows, metric, minimumSampleSize, baselineValue),
                buildFormatPatterns(eligibleRows, metric, minimumSampleSize, baselineValue),
                buildAccountPatterns(eligibleRows, scope, metric, minimumSampleSize, baselineValue)
        );
    }

    private List<AnalyticsPatternResponse> buildPostingWindowPatterns(List<AnalyticsPostRowResponse> rows,
                                                                      String metric,
                                                                      int minimumSampleSize,
                                                                      double baselineValue) {
        List<AnalyticsPatternResponse> patterns = new ArrayList<>();
        patterns.addAll(buildPatternsForDimension(
                rows,
                metric,
                baselineValue,
                minimumSampleSize,
                3,
                "POSTING_WINDOW",
                "weekday",
                row -> {
                    if (row.getPublishedAt() == null) {
                        return null;
                    }
                    DayOfWeek dayOfWeek = row.getPublishedAt().withOffsetSameInstant(ZoneOffset.UTC).getDayOfWeek();
                    return new PatternBucket(
                            dayOfWeek.name(),
                            dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                            dayOfWeek.getValue()
                    );
                }
        ));
        patterns.addAll(buildPatternsForDimension(
                rows,
                metric,
                baselineValue,
                minimumSampleSize,
                3,
                "POSTING_WINDOW",
                "hourBucket",
                row -> {
                    if (row.getPublishedAt() == null) {
                        return null;
                    }
                    int bucketStartHour = (row.getPublishedAt().withOffsetSameInstant(ZoneOffset.UTC).getHour() / 4) * 4;
                    return new PatternBucket(
                            String.format(Locale.ENGLISH, "%02d", bucketStartHour),
                            formatHourBucket(bucketStartHour),
                            bucketStartHour
                    );
                }
        ));

        return patterns.stream()
                .sorted(patternComparator())
                .limit(6)
                .toList();
    }

    private List<AnalyticsPatternResponse> buildFormatPatterns(List<AnalyticsPostRowResponse> rows,
                                                               String metric,
                                                               int minimumSampleSize,
                                                               double baselineValue) {
        List<AnalyticsPatternResponse> patterns = new ArrayList<>();
        patterns.addAll(buildPatternsForDimension(
                rows,
                metric,
                baselineValue,
                minimumSampleSize,
                3,
                "FORMAT",
                "postType",
                row -> new PatternBucket(row.getPostType(), labelForPostType(row.getPostType()), null)
        ));
        patterns.addAll(buildPatternsForDimension(
                rows,
                metric,
                baselineValue,
                minimumSampleSize,
                3,
                "FORMAT",
                "mediaFormat",
                row -> {
                    String mediaFormat = StringUtils.hasText(row.getMediaFormat()) ? row.getMediaFormat() : "TEXT_ONLY";
                    return new PatternBucket(mediaFormat, labelForMediaFormat(mediaFormat), null);
                }
        ));

        return patterns.stream()
                .sorted(patternComparator())
                .limit(6)
                .toList();
    }

    private List<AnalyticsPatternResponse> buildAccountPatterns(List<AnalyticsPostRowResponse> rows,
                                                                String scope,
                                                                String metric,
                                                                int minimumSampleSize,
                                                                double baselineValue) {
        if ("account".equals(scope)) {
            return List.of();
        }

        return buildPatternsForDimension(
                rows,
                metric,
                baselineValue,
                minimumSampleSize,
                4,
                "ACCOUNT",
                "account",
                row -> new PatternBucket(
                        accountKey(Provider.valueOf(row.getProvider()), row.getProviderUserId()),
                        accountBreakdownLabel(row),
                        null
                )
        );
    }

    private List<AnalyticsPatternResponse> buildPatternsForDimension(List<AnalyticsPostRowResponse> rows,
                                                                     String metric,
                                                                     double baselineValue,
                                                                     int minimumSampleSize,
                                                                     int limit,
                                                                     String patternType,
                                                                     String dimension,
                                                                     java.util.function.Function<AnalyticsPostRowResponse, PatternBucket> bucketResolver) {
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<PatternBucket, List<AnalyticsPostRowResponse>> rowsByBucket = new LinkedHashMap<>();
        for (AnalyticsPostRowResponse row : rows) {
            PatternBucket bucket = bucketResolver.apply(row);
            if (bucket == null) {
                continue;
            }
            rowsByBucket.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(row);
        }

        return rowsByBucket.entrySet().stream()
                .map(entry -> buildPatternResponse(
                        patternType,
                        dimension,
                        entry.getKey(),
                        entry.getValue(),
                        metric,
                        baselineValue,
                        minimumSampleSize
                ))
                .filter(Objects::nonNull)
                .sorted(patternComparator())
                .limit(limit)
                .toList();
    }

    private AnalyticsPatternResponse buildPatternResponse(String patternType,
                                                          String dimension,
                                                          PatternBucket bucket,
                                                          List<AnalyticsPostRowResponse> rows,
                                                          String metric,
                                                          double baselineValue,
                                                          int minimumSampleSize) {
        long sampleSize = rows.size();
        if (sampleSize < minimumSampleSize) {
            return null;
        }

        double observedValue = averageMetricValue(rows, metric);
        if (baselineValue <= 0.0 || observedValue <= baselineValue) {
            return null;
        }

        double liftPercent = ((observedValue - baselineValue) / baselineValue) * 100.0;
        return new AnalyticsPatternResponse(
                patternType,
                dimension,
                bucket.key(),
                bucket.label(),
                sampleSize,
                baselineValue,
                observedValue,
                liftPercent,
                patternConfidenceTier(sampleSize, liftPercent),
                patternEvidenceSummary(sampleSize, observedValue, baselineValue, liftPercent, metricDefinition(metric))
        );
    }

    private Comparator<AnalyticsPatternResponse> patternComparator() {
        return Comparator.comparingDouble(AnalyticsPatternResponse::getLiftPercent).reversed()
                .thenComparing(Comparator.comparingLong(AnalyticsPatternResponse::getSampleSize).reversed())
                .thenComparing(Comparator.comparingDouble(AnalyticsPatternResponse::getObservedValue).reversed())
                .thenComparing(AnalyticsPatternResponse::getLabel, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    private PatternContextKey patternContextKeyForRow(AnalyticsPostRowResponse row, String scope) {
        return switch (scope) {
            case "platform" -> new PatternContextKey(row.getProvider(), labelForProvider(row.getProvider()), labelForProvider(row.getProvider()));
            case "account" -> new PatternContextKey(
                    accountKey(Provider.valueOf(row.getProvider()), row.getProviderUserId()),
                    accountBreakdownLabel(row),
                    accountBreakdownLabel(row)
            );
            default -> new PatternContextKey("WORKSPACE", "Workspace", "Workspace");
        };
    }

    private boolean isPatternEligible(AnalyticsPostRowResponse row, String metric) {
        if (row == null || row.getPublishedAt() == null || !row.isHasLiveMetrics()) {
            return false;
        }
        if (isPatternFreshnessExcluded(row.getFreshnessStatus())) {
            return false;
        }
        if ("engagementRate".equals(metric)) {
            return row.getEngagementRate() != null;
        }
        return hasMetricValue(row, metric);
    }

    private boolean isPatternFreshnessExcluded(String freshnessStatus) {
        if (!StringUtils.hasText(freshnessStatus)) {
            return true;
        }
        return switch (freshnessStatus.toUpperCase(Locale.ENGLISH)) {
            case "NO_DATA", "STALE", "UNSUPPORTED" -> true;
            default -> false;
        };
    }

    private boolean hasMetricValue(AnalyticsPostRowResponse row, String metric) {
        return switch (metric) {
            case "impressions" -> row.getImpressions() != null;
            case "reach" -> row.getReach() != null;
            case "likes" -> row.getLikes() != null;
            case "comments" -> row.getComments() != null;
            case "shares" -> row.getShares() != null;
            case "saves" -> row.getSaves() != null;
            case "clicks" -> row.getClicks() != null;
            case "videoViews" -> row.getVideoViews() != null;
            case "watchTimeMinutes" -> row.getWatchTimeMinutes() != null;
            case "engagementRate" -> row.getEngagementRate() != null;
            default -> row.getEngagements() != null;
        };
    }

    private double averageMetricValue(List<AnalyticsPostRowResponse> rows, String metric) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        return rows.stream()
                .mapToDouble(row -> patternMetricValue(row, metric))
                .average()
                .orElse(0.0);
    }

    private double patternMetricValue(AnalyticsPostRowResponse row, String metric) {
        if ("engagementRate".equals(metric)) {
            return row.getEngagementRate() != null ? row.getEngagementRate() : 0.0;
        }
        return computeRowMetricValue(row, metric);
    }

    private String patternConfidenceTier(long sampleSize, double liftPercent) {
        if (sampleSize >= 8 && liftPercent >= 25.0) {
            return "HIGH";
        }
        if (sampleSize >= 5 && liftPercent >= 15.0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String patternEvidenceSummary(long sampleSize,
                                          double observedValue,
                                          double baselineValue,
                                          double liftPercent,
                                          MetricDefinition metricDefinition) {
        return String.format(
                Locale.ENGLISH,
                "%d posts averaged %.1f %s vs baseline %.1f, a %.1f%% lift.",
                sampleSize,
                observedValue,
                metricDefinition.label().toLowerCase(Locale.ENGLISH),
                baselineValue,
                liftPercent
        );
    }

    private BreakdownKey breakdownKeyForRow(AnalyticsPostRowResponse row, String dimension) {
        return switch (dimension) {
            case "platform" -> new BreakdownKey(row.getProvider(), labelForProvider(row.getProvider()), null);
            case "account" -> new BreakdownKey(
                    accountKey(Provider.valueOf(row.getProvider()), row.getProviderUserId()),
                    accountBreakdownLabel(row),
                    null
            );
            case "campaign" -> row.getCampaignId() != null
                    ? new BreakdownKey(
                    String.valueOf(row.getCampaignId()),
                    StringUtils.hasText(row.getCampaignLabel()) ? row.getCampaignLabel() : "Campaign #" + row.getCampaignId(),
                    null
            )
                    : new BreakdownKey("NO_CAMPAIGN", "No campaign", null);
            case "postType" -> new BreakdownKey(row.getPostType(), labelForPostType(row.getPostType()), null);
            case "mediaFormat" -> {
                String mediaFormat = StringUtils.hasText(row.getMediaFormat()) ? row.getMediaFormat() : "TEXT_ONLY";
                yield new BreakdownKey(mediaFormat, labelForMediaFormat(mediaFormat), null);
            }
            case "weekday" -> {
                if (row.getPublishedAt() == null) {
                    yield null;
                }
                DayOfWeek dayOfWeek = row.getPublishedAt().withOffsetSameInstant(ZoneOffset.UTC).getDayOfWeek();
                yield new BreakdownKey(
                        dayOfWeek.name(),
                        dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        dayOfWeek.getValue()
                );
            }
            default -> {
                if (row.getPublishedAt() == null) {
                    yield null;
                }
                int bucketStartHour = (row.getPublishedAt().withOffsetSameInstant(ZoneOffset.UTC).getHour() / 4) * 4;
                yield new BreakdownKey(
                        String.format(Locale.ENGLISH, "%02d", bucketStartHour),
                        formatHourBucket(bucketStartHour),
                        bucketStartHour
                );
            }
        };
    }

    private String accountBreakdownLabel(AnalyticsPostRowResponse row) {
        String accountName = StringUtils.hasText(row.getAccountName()) ? row.getAccountName() : row.getProviderUserId();
        return accountName + " · " + labelForProvider(row.getProvider());
    }

    private String labelForProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "Unknown";
        }
        return switch (provider.toUpperCase(Locale.ENGLISH)) {
            case "X" -> "X";
            case "YOUTUBE" -> "YouTube";
            case "LINKEDIN" -> "LinkedIn";
            case "INSTAGRAM" -> "Instagram";
            case "FACEBOOK" -> "Facebook";
            case "THREADS" -> "Threads";
            case "TIKTOK" -> "TikTok";
            default -> provider;
        };
    }

    private String labelForPostType(String postType) {
        if (!StringUtils.hasText(postType)) {
            return "Unknown";
        }
        return switch (postType.toUpperCase(Locale.ENGLISH)) {
            case "IMAGE" -> "Image";
            case "VIDEO" -> "Video";
            case "TEXT" -> "Text";
            default -> postType;
        };
    }

    private String labelForMediaFormat(String mediaFormat) {
        if (!StringUtils.hasText(mediaFormat)) {
            return "Text only";
        }
        return switch (mediaFormat.toUpperCase(Locale.ENGLISH)) {
            case "TEXT_ONLY" -> "Text only";
            case "IMAGE_ASSET" -> "Image asset";
            case "VIDEO_ASSET" -> "Video asset";
            case "MIXED_MEDIA" -> "Mixed media";
            case "OTHER_ASSET" -> "Other asset";
            default -> mediaFormat;
        };
    }

    private String formatHourBucket(int startHour) {
        return String.format(Locale.ENGLISH, "%02d:00-%02d:59 UTC", startHour, startHour + 3);
    }

    private String breakdownDimensionLabel(String dimension) {
        return switch (dimension) {
            case "platform" -> "Platform";
            case "account" -> "Account";
            case "campaign" -> "Campaign";
            case "postType" -> "Post Type";
            case "mediaFormat" -> "Media Format";
            case "weekday" -> "Weekday";
            default -> "Hour Bucket";
        };
    }

    private MetricDefinition metricDefinition(String metric) {
        return switch (metric) {
            case "impressions" -> new MetricDefinition(metric, "Impressions", "number");
            case "reach" -> new MetricDefinition(metric, "Reach", "number");
            case "likes" -> new MetricDefinition(metric, "Likes", "number");
            case "comments" -> new MetricDefinition(metric, "Comments", "number");
            case "shares" -> new MetricDefinition(metric, "Shares", "number");
            case "saves" -> new MetricDefinition(metric, "Saves", "number");
            case "clicks" -> new MetricDefinition(metric, "Clicks", "number");
            case "videoViews" -> new MetricDefinition(metric, "Video Views", "number");
            case "watchTimeMinutes" -> new MetricDefinition(metric, "Watch Time", "number");
            case "engagementRate" -> new MetricDefinition(metric, "Engagement Rate", "percent");
            default -> new MetricDefinition("engagements", "Engagements", "number");
        };
    }

    private double computePerformanceValue(List<AnalyticsPostRowResponse> rows, String metric) {
        if ("engagementRate".equals(metric)) {
            long impressions = sumLong(rows, AnalyticsPostRowResponse::getImpressions);
            long engagements = sumLong(rows, AnalyticsPostRowResponse::getEngagements);
            return impressions > 0 ? ((double) engagements / impressions) * 100.0 : 0.0;
        }

        return rows.stream()
                .mapToDouble(row -> computeRowMetricValue(row, metric))
                .sum();
    }

    private double computeRowMetricValue(AnalyticsPostRowResponse row, String metric) {
        return switch (metric) {
            case "impressions" -> numericValue(row.getImpressions());
            case "reach" -> numericValue(row.getReach());
            case "likes" -> numericValue(row.getLikes());
            case "comments" -> numericValue(row.getComments());
            case "shares" -> numericValue(row.getShares());
            case "saves" -> numericValue(row.getSaves());
            case "clicks" -> numericValue(row.getClicks());
            case "videoViews" -> numericValue(row.getVideoViews());
            case "watchTimeMinutes" -> numericValue(row.getWatchTimeMinutes());
            default -> numericValue(row.getEngagements());
        };
    }

    private AnalyticsOverviewMetricResponse metric(String key,
                                                  String label,
                                                  String format,
                                                  double currentValue,
                                                  double previousValue) {
        double deltaValue = currentValue - previousValue;
        Double deltaPercent = previousValue == 0.0 ? null : (deltaValue / previousValue) * 100.0;
        return new AnalyticsOverviewMetricResponse(key, label, format, currentValue, previousValue, deltaValue, deltaPercent);
    }

    private long sumLong(List<AnalyticsPostRowResponse> rows,
                         java.util.function.Function<AnalyticsPostRowResponse, Long> extractor) {
        return rows.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    private Comparator<AnalyticsPostRowResponse> buildComparator(String sortBy, String sortDirection) {
        Comparator<AnalyticsPostRowResponse> comparator = switch (sortBy) {
            case "impressions" -> longComparator(AnalyticsPostRowResponse::getImpressions);
            case "reach" -> longComparator(AnalyticsPostRowResponse::getReach);
            case "likes" -> longComparator(AnalyticsPostRowResponse::getLikes);
            case "comments" -> longComparator(AnalyticsPostRowResponse::getComments);
            case "shares" -> longComparator(AnalyticsPostRowResponse::getShares);
            case "saves" -> longComparator(AnalyticsPostRowResponse::getSaves);
            case "clicks" -> longComparator(AnalyticsPostRowResponse::getClicks);
            case "videoViews" -> longComparator(AnalyticsPostRowResponse::getVideoViews);
            case "watchTimeMinutes" -> longComparator(AnalyticsPostRowResponse::getWatchTimeMinutes);
            case "engagements" -> longComparator(AnalyticsPostRowResponse::getEngagements);
            case "engagementRate" -> doubleComparator(AnalyticsPostRowResponse::getEngagementRate);
            default -> Comparator.comparing(
                    AnalyticsPostRowResponse::getPublishedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };

        if ("desc".equals(sortDirection)) {
            comparator = comparator.reversed();
        }

        return comparator
                .thenComparing(AnalyticsPostRowResponse::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AnalyticsPostRowResponse::getPostId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Comparator<AnalyticsPostRowResponse> longComparator(java.util.function.Function<AnalyticsPostRowResponse, Long> extractor) {
        return Comparator.comparing(extractor, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Comparator<AnalyticsPostRowResponse> doubleComparator(java.util.function.Function<AnalyticsPostRowResponse, Double> extractor) {
        return Comparator.comparing(extractor, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private boolean hasRankingMetric(AnalyticsPostRowResponse row, String metric) {
        return switch (metric) {
            case "impressions" -> row.getImpressions() != null;
            case "reach" -> row.getReach() != null;
            case "likes" -> row.getLikes() != null;
            case "comments" -> row.getComments() != null;
            case "shares" -> row.getShares() != null;
            case "saves" -> row.getSaves() != null;
            case "clicks" -> row.getClicks() != null;
            case "videoViews" -> row.getVideoViews() != null;
            case "watchTimeMinutes" -> row.getWatchTimeMinutes() != null;
            case "engagementRate" -> row.getEngagementRate() != null;
            default -> row.getEngagements() != null;
        };
    }

    private ToDoubleFunction<AnalyticsPostRowResponse> rankingMetricExtractor(String metric) {
        return switch (metric) {
            case "impressions" -> row -> numericValue(row.getImpressions());
            case "reach" -> row -> numericValue(row.getReach());
            case "likes" -> row -> numericValue(row.getLikes());
            case "comments" -> row -> numericValue(row.getComments());
            case "shares" -> row -> numericValue(row.getShares());
            case "saves" -> row -> numericValue(row.getSaves());
            case "clicks" -> row -> numericValue(row.getClicks());
            case "videoViews" -> row -> numericValue(row.getVideoViews());
            case "watchTimeMinutes" -> row -> numericValue(row.getWatchTimeMinutes());
            case "engagementRate" -> row -> row.getEngagementRate() != null ? row.getEngagementRate() : 0.0;
            default -> row -> numericValue(row.getEngagements());
        };
    }

    private double numericValue(Long value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private Long sumAccountMetric(List<AccountAnalyticsSnapshotEntity> snapshots,
                                  java.util.function.Function<AccountAnalyticsSnapshotEntity, Long> extractor) {
        long total = 0L;
        boolean hasValue = false;
        for (AccountAnalyticsSnapshotEntity snapshot : snapshots) {
            Long value = extractor.apply(snapshot);
            if (value == null) {
                continue;
            }
            total += value;
            hasValue = true;
        }
        return hasValue ? total : null;
    }

    private Long latestAccountMetric(List<AccountAnalyticsSnapshotEntity> snapshots,
                                     java.util.function.Function<AccountAnalyticsSnapshotEntity, Long> extractor) {
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            Long value = extractor.apply(snapshots.get(i));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long computeFollowerDelta(List<AccountAnalyticsSnapshotEntity> snapshots) {
        Long first = null;
        Long last = null;
        for (AccountAnalyticsSnapshotEntity snapshot : snapshots) {
            Long value = snapshot.getFollowers();
            if (value == null) {
                continue;
            }
            if (first == null) {
                first = value;
            }
            last = value;
        }
        if (first == null || last == null) {
            return null;
        }
        return last - first;
    }

    private Long computeMetricDelta(List<AccountAnalyticsSnapshotEntity> snapshots,
                                    java.util.function.Function<AccountAnalyticsSnapshotEntity, Long> extractor) {
        Long first = null;
        Long last = null;
        for (AccountAnalyticsSnapshotEntity snapshot : snapshots) {
            Long value = extractor.apply(snapshot);
            if (value == null) {
                continue;
            }
            if (first == null) {
                first = value;
            }
            last = value;
        }
        if (first == null || last == null) {
            return null;
        }
        return last - first;
    }

    private Double ratioPercent(Long numerator, long denominator) {
        if (numerator == null || denominator <= 0L) {
            return null;
        }
        return (numerator.doubleValue() / denominator) * 100.0;
    }

    private Double ratioPercent(long numerator, long denominator) {
        if (denominator <= 0L) {
            return null;
        }
        return ((double) numerator / denominator) * 100.0;
    }

    private Double ratioPercent(double numerator, double denominator) {
        if (denominator <= 0.0) {
            return null;
        }
        return (numerator / denominator) * 100.0;
    }

    private boolean hasAny(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return true;
            }
        }
        return false;
    }

    private long value(Long value) {
        return value != null ? value : 0L;
    }

    private String normalizeSortBy(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return "publishedAt";
        }
        return switch (sortBy) {
            case "impressions", "reach", "likes", "comments", "shares", "saves",
                    "clicks", "videoViews", "watchTimeMinutes", "engagements", "engagementRate", "publishedAt" -> sortBy;
            default -> "publishedAt";
        };
    }

    private String normalizeSortDirection(String sortDirection) {
        return "asc".equalsIgnoreCase(sortDirection) ? "asc" : "desc";
    }

    private String normalizeTrendMetric(String metric) {
        return normalizeRankingMetric(metric);
    }

    private String normalizePatternMetric(String metric) {
        return normalizeRankingMetric(metric);
    }

    private String normalizeRankingMetric(String metric) {
        if (!StringUtils.hasText(metric)) {
            return "engagements";
        }
        return switch (metric) {
            case "impressions", "reach", "likes", "comments", "shares", "saves",
                    "clicks", "videoViews", "watchTimeMinutes", "engagementRate", "engagements" -> metric;
            default -> "engagements";
        };
    }

    private String normalizeBreakdownMetric(String metric) {
        if (!StringUtils.hasText(metric)) {
            return "engagements";
        }
        return switch (metric) {
            case "impressions", "reach", "likes", "comments", "shares", "saves",
                    "clicks", "videoViews", "watchTimeMinutes", "engagements" -> metric;
            default -> "engagements";
        };
    }

    private String normalizeBreakdownDimension(String dimension) {
        if (!StringUtils.hasText(dimension)) {
            return "platform";
        }
        return switch (dimension) {
            case "platform", "account", "campaign", "postType", "mediaFormat", "weekday", "hourBucket" -> dimension;
            default -> "platform";
        };
    }

    private String normalizePatternScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "workspace";
        }
        return switch (scope) {
            case "workspace", "platform", "account" -> scope;
            default -> "workspace";
        };
    }

    private String patternScopeLabel(String scope) {
        return switch (scope) {
            case "platform" -> "Platform View";
            case "account" -> "Account View";
            default -> "Workspace View";
        };
    }

    private int minimumPatternSampleSize() {
        return 3;
    }

    private String labelForCampaign(PostCollectionEntity collection) {
        String description = collection.getDescription();
        if (!StringUtils.hasText(description)) {
            return "Campaign #" + collection.getId();
        }
        String normalized = description.strip();
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 45) + "...";
    }

    private String requireWorkspaceId() {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        if (!StringUtils.hasText(workspaceId)) {
            throw new IllegalStateException("Workspace context is required for analytics.");
        }
        return workspaceId;
    }

    private String accountKey(ConnectedAccount account) {
        return accountKey(Provider.valueOf(account.getPlatform().name().toUpperCase()), account.getProviderUserId());
    }

    private String accountKey(Provider provider, String providerUserId) {
        return provider.name() + ":" + providerUserId;
    }

    private record WorkspaceAnalyticsDataset(List<AnalyticsPostRowResponse> currentRows,
                                             List<AnalyticsPostRowResponse> previousRows) {
    }

    private record OverviewAggregate(double impressions,
                                     double reach,
                                     double engagements,
                                     double engagementRate,
                                     double clicks,
                                     double videoViews,
                                     double watchTimeMinutes,
                                     double postsPublished) {
    }

    private record MetricDefinition(String key, String label, String format) {
    }

    private record BreakdownKey(String key, String label, Integer sortOrder) {
    }

    private record PatternContextKey(String key, String label, String sortOrder) {
    }

    private record PatternBucket(String key, String label, Integer sortOrder) {
    }

    private record RecommendationCandidate(String recommendationKey,
                                           String sourceType,
                                           String contextLabel,
                                           String title,
                                           String actionSummary,
                                           String evidenceSummary,
                                           String confidenceTier,
                                           String priority,
                                           double expectedImpactScore,
                                           OffsetDateTime timeWindowStartAt,
                                           OffsetDateTime timeWindowEndAt) {
    }

    private record ContributionTarget(String dimension, AnalyticsBreakdownRowResponse row) {
    }

    private record ForecastSlotBucket(DayOfWeek dayOfWeek,
                                      int startHour,
                                      String key,
                                      String label) {
    }

    private record ForecastSlotCandidate(ForecastSlotBucket bucket,
                                         List<AnalyticsPostRowResponse> rows,
                                         double observedValue,
                                         double liftPercent,
                                         double score,
                                         AnalyticsForecastRangeResponse range,
                                         String confidenceTier) {
    }

    private record PostComparableGroup(String label, List<AnalyticsPostRowResponse> rows) {
    }

    private static final class BreakdownAccumulator {
        private final String key;
        private final String label;
        private final Integer sortOrder;
        private long postsPublished;
        private double performanceValue;

        private BreakdownAccumulator(String key, String label, Integer sortOrder) {
            this.key = key;
            this.label = label;
            this.sortOrder = sortOrder;
        }

        private void increment(double deltaPerformanceValue) {
            this.postsPublished += 1L;
            this.performanceValue += deltaPerformanceValue;
        }

        private String key() {
            return key;
        }

        private String label() {
            return label;
        }

        private Integer sortOrder() {
            return sortOrder;
        }

        private long postsPublished() {
            return postsPublished;
        }

        private double performanceValue() {
            return performanceValue;
        }
    }

    private record LinkedInPageActivityAggregate(String providerUserId,
                                                 String accountName,
                                                 Long followers,
                                                 Long followerDelta,
                                                 Long pageViews,
                                                 Long uniquePageViews,
                                                 Long clicks,
                                                 LocalDate lastSnapshotDate) {
    }

    private record YouTubeChannelActivityAggregate(String providerUserId,
                                                   String accountName,
                                                   Long followers,
                                                   Long subscriberDelta,
                                                   Long videoViews,
                                                   Long likes,
                                                   Long comments,
                                                   Long shares,
                                                   Long watchTimeMinutes,
                                                   LocalDate lastSnapshotDate) {
    }

    private AnalyticsTikTokCreatorActivityResponse emptyTikTokCreatorActivity(WorkspaceAnalyticsSlice slice) {
        return new AnalyticsTikTokCreatorActivityResponse(
                slice.currentRangeLabel(),
                slice.currentStartAt().toLocalDate(),
                slice.currentEndAt().toLocalDate(),
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                List.of(),
                List.of()
        );
    }

    private record TikTokCreatorActivityAggregate(String providerUserId,
                                                  String accountName,
                                                  Long followers,
                                                  Long following,
                                                  Long likesTotal,
                                                  Long videoCount,
                                                  Long followerDelta,
                                                  Long likesDelta,
                                                  Long videoDelta,
                                                  LocalDate lastSnapshotDate) {
    }

    private record WorkspaceAnalyticsSlice(int days,
                                           Provider provider,
                                           String providerUserId,
                                           Long campaignId,
                                           PostType postType,
                                           OffsetDateTime currentStartAt,
                                           OffsetDateTime currentEndAt,
                                           OffsetDateTime previousStartAt,
                                           OffsetDateTime previousEndAt) {

        private static WorkspaceAnalyticsSlice of(int days,
                                                  String platform,
                                                  String providerUserId,
                                                  Long campaignId,
                                                  String contentType) {
            int normalizedDays = Math.max(days, 1);
            OffsetDateTime currentEndAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
            OffsetDateTime currentStartAt = currentEndAt.minusDays(normalizedDays);
            OffsetDateTime previousEndAt = currentStartAt;
            OffsetDateTime previousStartAt = previousEndAt.minusDays(normalizedDays);

            return new WorkspaceAnalyticsSlice(
                    normalizedDays,
                    StringUtils.hasText(platform) ? Provider.valueOf(platform.toUpperCase()) : null,
                    StringUtils.hasText(providerUserId) ? providerUserId : null,
                    campaignId,
                    StringUtils.hasText(contentType) ? PostType.valueOf(contentType.toUpperCase()) : null,
                    currentStartAt,
                    currentEndAt,
                    previousStartAt,
                    previousEndAt
            );
        }

        private boolean matchesCurrent(PostEntity post) {
            return matches(post, currentStartAt, currentEndAt);
        }

        private boolean matchesPrevious(PostEntity post) {
            return matches(post, previousStartAt, previousEndAt);
        }

        private boolean matches(PostEntity post, OffsetDateTime from, OffsetDateTime to) {
            if (post == null || post.getScheduledTime() == null || post.getScheduledTime().isAfter(currentEndAt)) {
                return false;
            }
            if (provider != null && !provider.equals(post.getProvider())) {
                return false;
            }
            if (StringUtils.hasText(providerUserId) && !providerUserId.equals(post.getProviderUserId())) {
                return false;
            }
            if (campaignId != null) {
                if (post.getPostCollection() == null || !campaignId.equals(post.getPostCollection().getId())) {
                    return false;
                }
            }
            if (postType != null && !postType.equals(post.getPostType())) {
                return false;
            }
            return !post.getScheduledTime().isBefore(from) && post.getScheduledTime().isBefore(to);
        }

        private String currentRangeLabel() {
            return "Last " + days + " days";
        }

        private String previousRangeLabel() {
            return "Previous " + days + " days";
        }
    }
}
