package com.tonyghouse.socialraven.service.analytics;

import com.tonyghouse.socialraven.constant.AnalyticsFreshnessStatus;
import com.tonyghouse.socialraven.constant.PostType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsBreakdownResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsBreakdownRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsLinkedInPageActivityResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsLinkedInPageActivityRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewMetricResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostRankingsResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsPostTableResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTikTokCreatorActivityResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTikTokCreatorActivityRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTikTokCreatorActivityTrendPointResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTrendExplorerPointResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsTrendExplorerResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsYouTubeChannelActivityResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsYouTubeChannelActivityRowResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsYouTubeChannelActivityTrendPointResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsWorkspaceOverviewResponse;
import com.tonyghouse.socialraven.entity.AccountAnalyticsSnapshotEntity;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.WorkspacePostAnalyticsEntity;
import com.tonyghouse.socialraven.model.AnalyticsMetricAvailabilityWindow;
import com.tonyghouse.socialraven.repo.AccountAnalyticsSnapshotRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.repo.WorkspacePostAnalyticsRepo;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AnalyticsWorkspaceService {

    private final PostRepo postRepo;
    private final WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo;
    private final AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo;
    private final AccountProfileService accountProfileService;

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
