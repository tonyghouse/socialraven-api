package com.ghouse.socialraven.service.analytics;

import com.ghouse.socialraven.dto.analytics.*;
import com.ghouse.socialraven.entity.AccountAnalyticsSnapshotEntity;
import com.ghouse.socialraven.entity.PostAnalyticsSnapshotEntity;
import com.ghouse.socialraven.entity.PostEntity;
import com.ghouse.socialraven.repo.AccountAnalyticsSnapshotRepo;
import com.ghouse.socialraven.repo.PostAnalyticsSnapshotRepo;
import com.ghouse.socialraven.repo.PostRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsApiService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsApiService.class);

    @Autowired
    private PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo;

    @Autowired
    private AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo;

    @Autowired
    private PostRepo postRepo;

    // -------------------------------------------------------------------------
    // Overview
    // -------------------------------------------------------------------------

    public AnalyticsOverviewResponse getOverview(String userId, int days) {
        try {
            OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
            List<PostAnalyticsSnapshotEntity> snapshots = postAnalyticsSnapshotRepo.findByUserIdSince(userId, since);

            if (snapshots.isEmpty()) {
                return new AnalyticsOverviewResponse();
            }

            long totalImpressions = sumLong(snapshots, PostAnalyticsSnapshotEntity::getImpressions);
            long totalReach       = sumLong(snapshots, PostAnalyticsSnapshotEntity::getReach);
            long totalLikes       = sumLong(snapshots, PostAnalyticsSnapshotEntity::getLikes);
            long totalComments    = sumLong(snapshots, PostAnalyticsSnapshotEntity::getComments);
            long totalShares      = sumLong(snapshots, PostAnalyticsSnapshotEntity::getShares);
            long totalVideoViews  = sumLong(snapshots, PostAnalyticsSnapshotEntity::getVideoViews);

            long totalEngagements = totalLikes + totalComments + totalShares;
            double avgEngagementRate = totalImpressions > 0
                    ? (double) totalEngagements / totalImpressions * 100.0
                    : 0.0;

            // Follower growth: sum net change across all account snapshots in window
            Set<String> providerUserIds = snapshots.stream()
                    .map(PostAnalyticsSnapshotEntity::getProviderPostId)
                    .collect(Collectors.toSet());

            // Distinct providerUserIds from posts that own these snapshots
            List<Long> postIds = snapshots.stream()
                    .map(PostAnalyticsSnapshotEntity::getPostId)
                    .distinct()
                    .collect(Collectors.toList());
            List<PostEntity> posts = postRepo.findAllById(postIds);
            Set<String> distinctProviderUserIds = posts.stream()
                    .map(PostEntity::getProviderUserId)
                    .collect(Collectors.toSet());

            long followerGrowth = computeFollowerGrowth(new ArrayList<>(distinctProviderUserIds), since);

            int totalPosts = (int) snapshots.stream()
                    .map(PostAnalyticsSnapshotEntity::getPostId)
                    .distinct()
                    .count();

            return new AnalyticsOverviewResponse(
                    totalImpressions, totalReach, totalLikes, totalComments,
                    totalShares, totalVideoViews, followerGrowth, totalPosts, avgEngagementRate);

        } catch (Exception e) {
            log.error("Error computing analytics overview for userId={}", userId, e);
            return new AnalyticsOverviewResponse();
        }
    }

    // -------------------------------------------------------------------------
    // Platform stats
    // -------------------------------------------------------------------------

    public List<PlatformStatsResponse> getPlatformStats(String userId, int days) {
        try {
            OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
            List<PostAnalyticsSnapshotEntity> snapshots = postAnalyticsSnapshotRepo.findByUserIdSince(userId, since);

            if (snapshots.isEmpty()) {
                return Collections.emptyList();
            }

            // Group snapshots by provider
            Map<String, List<PostAnalyticsSnapshotEntity>> byProvider = snapshots.stream()
                    .collect(Collectors.groupingBy(PostAnalyticsSnapshotEntity::getProvider));

            // Load posts to gather providerUserIds per provider for follower growth
            List<Long> postIds = snapshots.stream()
                    .map(PostAnalyticsSnapshotEntity::getPostId)
                    .distinct()
                    .collect(Collectors.toList());
            List<PostEntity> posts = postRepo.findAllById(postIds);
            Map<Long, PostEntity> postMap = posts.stream()
                    .collect(Collectors.toMap(PostEntity::getId, p -> p));

            List<PlatformStatsResponse> result = new ArrayList<>();

            for (Map.Entry<String, List<PostAnalyticsSnapshotEntity>> entry : byProvider.entrySet()) {
                String provider = entry.getKey();
                List<PostAnalyticsSnapshotEntity> provSnaps = entry.getValue();

                long impressions = sumLong(provSnaps, PostAnalyticsSnapshotEntity::getImpressions);
                long reach       = sumLong(provSnaps, PostAnalyticsSnapshotEntity::getReach);
                long likes       = sumLong(provSnaps, PostAnalyticsSnapshotEntity::getLikes);
                long comments    = sumLong(provSnaps, PostAnalyticsSnapshotEntity::getComments);
                long shares      = sumLong(provSnaps, PostAnalyticsSnapshotEntity::getShares);
                long clicks      = sumLong(provSnaps, PostAnalyticsSnapshotEntity::getClicks);
                long videoViews  = sumLong(provSnaps, PostAnalyticsSnapshotEntity::getVideoViews);
                long engagements = likes + comments + shares;
                double engagementRate = impressions > 0 ? (double) engagements / impressions * 100.0 : 0.0;

                Set<String> providerUserIdsForProvider = provSnaps.stream()
                        .map(s -> {
                            PostEntity p = postMap.get(s.getPostId());
                            return p != null ? p.getProviderUserId() : null;
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                long followerGrowth = computeFollowerGrowth(new ArrayList<>(providerUserIdsForProvider), since);

                int postsPublished = (int) provSnaps.stream()
                        .map(PostAnalyticsSnapshotEntity::getPostId)
                        .distinct()
                        .count();

                result.add(new PlatformStatsResponse(
                        provider, impressions, reach, likes, comments, shares,
                        clicks, videoViews, followerGrowth, postsPublished, engagementRate));
            }

            return result;

        } catch (Exception e) {
            log.error("Error computing platform stats for userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Top posts
    // -------------------------------------------------------------------------

    public List<TopPostResponse> getTopPosts(String userId, int days, String snapshotType) {
        try {
            OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
            List<PostAnalyticsSnapshotEntity> snapshots =
                    postAnalyticsSnapshotRepo.findTopPostsByUserIdAndSnapshotType(userId, snapshotType, since);

            if (snapshots.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> postIds = snapshots.stream()
                    .map(PostAnalyticsSnapshotEntity::getPostId)
                    .distinct()
                    .collect(Collectors.toList());
            List<PostEntity> posts = postRepo.findAllById(postIds);
            Map<Long, PostEntity> postMap = posts.stream()
                    .collect(Collectors.toMap(PostEntity::getId, p -> p));

            List<TopPostResponse> result = new ArrayList<>();
            for (PostAnalyticsSnapshotEntity s : snapshots) {
                PostEntity post = postMap.get(s.getPostId());
                String content = null;
                OffsetDateTime publishedAt = null;

                if (post != null) {
                    publishedAt = post.getScheduledTime();
                    // Load collection for description via a separate fetch
                    try {
                        PostEntity fullPost = postRepo.findPostWithCollectionAndMedia(post.getId());
                        if (fullPost != null && fullPost.getPostCollection() != null) {
                            content = fullPost.getPostCollection().getDescription();
                        }
                    } catch (Exception ex) {
                        log.warn("Could not load post collection for postId={}", post.getId(), ex);
                    }
                }

                long likes     = coalesce(s.getLikes());
                long comments  = coalesce(s.getComments());
                long shares    = coalesce(s.getShares());
                long impressions = coalesce(s.getImpressions());
                long reach     = coalesce(s.getReach());
                long engagements = likes + comments + shares;
                double engagementRate = impressions > 0 ? (double) engagements / impressions * 100.0 : 0.0;

                result.add(new TopPostResponse(
                        s.getPostId(),
                        s.getProvider(),
                        s.getProviderPostId(),
                        content,
                        publishedAt,
                        s.getSnapshotType(),
                        impressions,
                        reach,
                        likes,
                        comments,
                        shares,
                        engagementRate));
            }

            return result;

        } catch (Exception e) {
            log.error("Error fetching top posts for userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Timeline
    // -------------------------------------------------------------------------

    public List<TimelinePointResponse> getTimeline(String userId, int days) {
        try {
            OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
            List<PostAnalyticsSnapshotEntity> snapshots = postAnalyticsSnapshotRepo.findByUserIdSince(userId, since);

            if (snapshots.isEmpty()) {
                return Collections.emptyList();
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            // Group by (date, provider)
            Map<String, Map<String, Long>> grouped = new LinkedHashMap<>();
            for (PostAnalyticsSnapshotEntity s : snapshots) {
                if (s.getFetchedAt() == null) continue;
                String date = s.getFetchedAt().atZoneSameInstant(ZoneOffset.UTC).format(fmt);
                String provider = s.getProvider();
                String key = date + "|" + provider;

                long engagements = coalesce(s.getLikes()) + coalesce(s.getComments()) + coalesce(s.getShares());

                grouped.computeIfAbsent(date, d -> new LinkedHashMap<>())
                        .merge(provider, engagements, Long::sum);
            }

            List<TimelinePointResponse> result = new ArrayList<>();
            for (Map.Entry<String, Map<String, Long>> dateEntry : grouped.entrySet()) {
                for (Map.Entry<String, Long> provEntry : dateEntry.getValue().entrySet()) {
                    result.add(new TimelinePointResponse(dateEntry.getKey(), provEntry.getKey(), provEntry.getValue()));
                }
            }

            result.sort(Comparator.comparing(TimelinePointResponse::getDate)
                    .thenComparing(TimelinePointResponse::getProvider));

            return result;

        } catch (Exception e) {
            log.error("Error computing timeline for userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Heatmap
    // -------------------------------------------------------------------------

    public List<HeatmapCellResponse> getHeatmap(String userId) {
        try {
            // Use all-time snapshots for heatmap (no time window)
            OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(365);
            List<PostAnalyticsSnapshotEntity> snapshots = postAnalyticsSnapshotRepo.findByUserIdSince(userId, since);

            if (snapshots.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> postIds = snapshots.stream()
                    .map(PostAnalyticsSnapshotEntity::getPostId)
                    .distinct()
                    .collect(Collectors.toList());
            List<PostEntity> posts = postRepo.findAllById(postIds);
            Map<Long, PostEntity> postMap = posts.stream()
                    .collect(Collectors.toMap(PostEntity::getId, p -> p));

            // Build a map: postId -> first snapshot engagements (avoid double-counting per post)
            // Use the latest snapshot per post to get best data
            Map<Long, PostAnalyticsSnapshotEntity> latestPerPost = new LinkedHashMap<>();
            for (PostAnalyticsSnapshotEntity s : snapshots) {
                latestPerPost.merge(s.getPostId(), s, (existing, incoming) -> {
                    if (existing.getFetchedAt() == null) return incoming;
                    if (incoming.getFetchedAt() == null) return existing;
                    return incoming.getFetchedAt().isAfter(existing.getFetchedAt()) ? incoming : existing;
                });
            }

            // Group by (dayOfWeek, hourOfDay) using the post's scheduledTime
            // dayOfWeek: 1=Monday...7=Sunday per ISO-8601
            Map<String, List<Long>> cellEngagements = new LinkedHashMap<>();
            for (Map.Entry<Long, PostAnalyticsSnapshotEntity> entry : latestPerPost.entrySet()) {
                Long postId = entry.getKey();
                PostAnalyticsSnapshotEntity s = entry.getValue();
                PostEntity post = postMap.get(postId);

                if (post == null || post.getScheduledTime() == null) continue;

                OffsetDateTime scheduledTime = post.getScheduledTime().withOffsetSameInstant(ZoneOffset.UTC);
                int dow  = scheduledTime.getDayOfWeek().getValue(); // 1=Mon, 7=Sun
                int hour = scheduledTime.getHour();

                long engagements = coalesce(s.getLikes()) + coalesce(s.getComments()) + coalesce(s.getShares());
                String cellKey = dow + ":" + hour;
                cellEngagements.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(engagements);
            }

            List<HeatmapCellResponse> result = new ArrayList<>();
            for (Map.Entry<String, List<Long>> entry : cellEngagements.entrySet()) {
                String[] parts = entry.getKey().split(":");
                int dow  = Integer.parseInt(parts[0]);
                int hour = Integer.parseInt(parts[1]);
                List<Long> engList = entry.getValue();
                double avg = engList.stream().mapToLong(Long::longValue).average().orElse(0.0);
                result.add(new HeatmapCellResponse(dow, hour, avg, engList.size()));
            }

            result.sort(Comparator.comparingInt(HeatmapCellResponse::getDayOfWeek)
                    .thenComparingInt(HeatmapCellResponse::getHourOfDay));

            return result;

        } catch (Exception e) {
            log.error("Error computing heatmap for userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private long computeFollowerGrowth(List<String> providerUserIds, OffsetDateTime since) {
        if (providerUserIds == null || providerUserIds.isEmpty()) return 0L;
        try {
            LocalDate fromDate = since.toLocalDate();
            LocalDate toDate   = LocalDate.now(ZoneOffset.UTC);
            List<AccountAnalyticsSnapshotEntity> accountSnaps =
                    accountAnalyticsSnapshotRepo.findByProviderUserIdInAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                            providerUserIds, fromDate, toDate);
            if (accountSnaps.isEmpty()) return 0L;

            // For each providerUserId, compute last - first followers
            Map<String, List<AccountAnalyticsSnapshotEntity>> byPuid = accountSnaps.stream()
                    .collect(Collectors.groupingBy(AccountAnalyticsSnapshotEntity::getProviderUserId));

            long total = 0L;
            for (List<AccountAnalyticsSnapshotEntity> snaps : byPuid.values()) {
                if (snaps.size() < 2) continue;
                Long first = snaps.get(0).getFollowers();
                Long last  = snaps.get(snaps.size() - 1).getFollowers();
                if (first != null && last != null) {
                    total += (last - first);
                }
            }
            return total;
        } catch (Exception e) {
            log.warn("Could not compute follower growth", e);
            return 0L;
        }
    }

    @FunctionalInterface
    private interface LongExtractor {
        Long extract(PostAnalyticsSnapshotEntity e);
    }

    private long sumLong(List<PostAnalyticsSnapshotEntity> list, LongExtractor extractor) {
        long sum = 0L;
        for (PostAnalyticsSnapshotEntity e : list) {
            Long val = extractor.extract(e);
            if (val != null) sum += val;
        }
        return sum;
    }

    private long coalesce(Long value) {
        return value != null ? value : 0L;
    }
}
