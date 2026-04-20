package com.tonyghouse.socialraven.service.analytics;

import com.tonyghouse.socialraven.constant.AnalyticsCollectionMode;
import com.tonyghouse.socialraven.constant.AnalyticsCoverageState;
import com.tonyghouse.socialraven.constant.AnalyticsFreshnessStatus;
import com.tonyghouse.socialraven.constant.AnalyticsJobStatus;
import com.tonyghouse.socialraven.constant.AnalyticsJobTrigger;
import com.tonyghouse.socialraven.constant.AnalyticsJobType;
import com.tonyghouse.socialraven.constant.PostType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsFilterOptionsResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsManualRefreshProviderResultResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsManualRefreshResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsProviderCoverageResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsSelectOptionResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsShellResponse;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsShellSummaryResponse;
import com.tonyghouse.socialraven.entity.AccountAnalyticsSnapshotEntity;
import com.tonyghouse.socialraven.entity.AnalyticsJobEntity;
import com.tonyghouse.socialraven.entity.AnalyticsProviderCoverageEntity;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.entity.PostAnalyticsSnapshotEntity;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.WorkspacePostAnalyticsEntity;
import com.tonyghouse.socialraven.model.AnalyticsMetricAvailabilityWindow;
import com.tonyghouse.socialraven.model.AnalyticsProviderCapabilities;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AnalyticsBackboneService {

    private static final int MANUAL_REFRESH_COOLDOWN_MINUTES = 15;

    private final AccountProfileService accountProfileService;
    private final OAuthInfoRepo oauthInfoRepo;
    private final PostRepo postRepo;
    private final WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo;
    private final PostAnalyticsSnapshotRepo postAnalyticsSnapshotRepo;
    private final AccountAnalyticsSnapshotRepo accountAnalyticsSnapshotRepo;
    private final AnalyticsProviderCoverageRepo analyticsProviderCoverageRepo;
    private final AnalyticsJobRepo analyticsJobRepo;
    private final PostRedisService postRedisService;

    @Transactional
    public AnalyticsShellResponse getShell(int days,
                                           String platform,
                                           String providerUserId,
                                           Long campaignId,
                                           String contentType) {
        String workspaceId = requireWorkspaceId();
        AnalyticsSlice slice = AnalyticsSlice.of(days, platform, providerUserId, campaignId, contentType);

        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(workspaceId, false);
        List<PostEntity> analyticsPosts = postRepo.findAnalyticsPostsByWorkspaceId(workspaceId);
        List<WorkspacePostAnalyticsEntity> currentMetrics = workspacePostAnalyticsRepo.findAllByWorkspaceId(workspaceId);
        List<PostAnalyticsSnapshotEntity> milestoneSnapshots = postAnalyticsSnapshotRepo.findAllByWorkspaceId(workspaceId);
        List<AccountAnalyticsSnapshotEntity> accountSnapshots = accountAnalyticsSnapshotRepo.findAllByWorkspaceId(workspaceId);

        AnalyticsFilterOptionsResponse filters = buildFilterOptions(connectedAccounts, analyticsPosts);
        AnalyticsShellSummaryResponse summary = buildSummary(
                workspaceId,
                slice,
                connectedAccounts,
                analyticsPosts,
                currentMetrics,
                milestoneSnapshots,
                accountSnapshots
        );
        List<AnalyticsProviderCoverageResponse> coverage = buildCoverageResponses(
                workspaceId,
                slice,
                connectedAccounts,
                analyticsPosts,
                currentMetrics,
                milestoneSnapshots,
                accountSnapshots
        );

        return new AnalyticsShellResponse(summary, filters, coverage);
    }

    @Transactional
    public AnalyticsManualRefreshResponse requestManualRefresh(String platform, String providerUserId) {
        String workspaceId = requireWorkspaceId();
        AnalyticsSlice slice = AnalyticsSlice.of(30, platform, providerUserId, null, null);
        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(workspaceId, false);

        List<ConnectedAccount> targets = connectedAccounts.stream()
                .filter(account -> slice.matches(account))
                .toList();

        if (targets.isEmpty()) {
            return new AnalyticsManualRefreshResponse(List.of(
                    new AnalyticsManualRefreshProviderResultResponse(
                            normalizeProvider(platform),
                            0,
                            "SKIPPED",
                            "No matching connected accounts in this workspace slice.",
                            null
                    )
            ));
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<AnalyticsManualRefreshProviderResultResponse> results = new ArrayList<>();
        Map<String, Double> redisJobs = new LinkedHashMap<>();

        Map<Provider, List<ConnectedAccount>> byProvider = targets.stream()
                .collect(Collectors.groupingBy(
                        account -> Provider.valueOf(account.getPlatform().name().toUpperCase()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<Provider, List<ConnectedAccount>> entry : byProvider.entrySet()) {
            Provider provider = entry.getKey();
            AnalyticsProviderCapabilities capabilities = AnalyticsProviderCapabilities.forProvider(provider);
            AnalyticsProviderCoverageEntity coverage = ensureCoverageRow(workspaceId, provider);

            if (capabilities == null
                    || !capabilities.collectionModes().contains(AnalyticsCollectionMode.MANUAL_REFRESH)
                    || !supportsLivePostCollection(capabilities)) {
                results.add(new AnalyticsManualRefreshProviderResultResponse(
                        provider.name(),
                        0,
                        "SKIPPED",
                        "Manual refresh is not available for this provider.",
                        null
                ));
                continue;
            }

            OffsetDateTime nextAllowedAt = nextManualRefreshAt(coverage.getLastManualRefreshRequestedAt());
            if (nextAllowedAt != null && nextAllowedAt.isAfter(now)) {
                results.add(new AnalyticsManualRefreshProviderResultResponse(
                        provider.name(),
                        0,
                        "COOLDOWN",
                        "Manual refresh is on cooldown for this provider in this workspace.",
                        nextAllowedAt
                ));
                continue;
            }

            int scheduledJobs = 0;
            for (ConnectedAccount account : entry.getValue()) {
                AnalyticsJobEntity job = new AnalyticsJobEntity();
                job.setWorkspaceId(workspaceId);
                job.setProvider(provider);
                job.setProviderUserId(account.getProviderUserId());
                job.setJobType(AnalyticsJobType.PROVIDER_RECONCILE);
                job.setTriggerType(AnalyticsJobTrigger.MANUAL);
                job.setDedupeKey(buildProviderReconcileKey(workspaceId, provider, account.getProviderUserId(), now, true));
                job.setDueAt(now);
                job.setStatus(AnalyticsJobStatus.PENDING);

                if (analyticsJobRepo.findByDedupeKey(job.getDedupeKey()).isPresent()) {
                    continue;
                }

                AnalyticsJobEntity saved = analyticsJobRepo.save(job);
                redisJobs.put(String.valueOf(saved.getId()), (double) now.toInstant().toEpochMilli());
                scheduledJobs++;
            }

            coverage.setLastManualRefreshRequestedAt(now);
            analyticsProviderCoverageRepo.save(coverage);

            results.add(new AnalyticsManualRefreshProviderResultResponse(
                    provider.name(),
                    scheduledJobs,
                    scheduledJobs > 0 ? "SCHEDULED" : "SKIPPED",
                    scheduledJobs > 0 ? "Refresh jobs queued." : "No new refresh jobs were created.",
                    nextManualRefreshAt(now)
            ));
        }

        if (!redisJobs.isEmpty()) {
            postRedisService.addIds("analytics-snapshot-pool", redisJobs);
        }

        return new AnalyticsManualRefreshResponse(results);
    }

    @Transactional
    public void scheduleDailyAccountSyncs() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
        LocalDate snapshotDate = now.toLocalDate();
        Map<String, Double> redisJobs = new LinkedHashMap<>();

        for (OAuthInfoEntity oauthInfo : oauthInfoRepo.findAll()) {
            if (!StringUtils.hasText(oauthInfo.getWorkspaceId())) {
                continue;
            }

            AnalyticsProviderCapabilities capabilities = AnalyticsProviderCapabilities.forProvider(oauthInfo.getProvider());
            if (capabilities == null
                    || !capabilities.collectionModes().contains(AnalyticsCollectionMode.DAILY_ACCOUNT_SYNC)
                    || !supportsLiveAccountCollection(capabilities)) {
                continue;
            }

            String dedupeKey = "account-daily:%s:%s:%s:%s".formatted(
                    oauthInfo.getWorkspaceId(),
                    oauthInfo.getProvider().name(),
                    oauthInfo.getProviderUserId(),
                    snapshotDate
            );
            if (analyticsJobRepo.findByDedupeKey(dedupeKey).isPresent()) {
                continue;
            }

            AnalyticsJobEntity job = new AnalyticsJobEntity();
            job.setWorkspaceId(oauthInfo.getWorkspaceId());
            job.setProvider(oauthInfo.getProvider());
            job.setProviderUserId(oauthInfo.getProviderUserId());
            job.setJobType(AnalyticsJobType.ACCOUNT_DAILY);
            job.setTriggerType(AnalyticsJobTrigger.SCHEDULED_DAILY);
            job.setDedupeKey(dedupeKey);
            job.setDueAt(now);
            job.setStatus(AnalyticsJobStatus.PENDING);

            AnalyticsJobEntity saved = analyticsJobRepo.save(job);
            redisJobs.put(String.valueOf(saved.getId()), (double) now.toInstant().toEpochMilli());
        }

        if (!redisJobs.isEmpty()) {
            postRedisService.addIds("analytics-snapshot-pool", redisJobs);
        }
    }

    @Transactional
    public void scheduleNightlyProviderReconciles() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
        LocalDate snapshotDate = now.toLocalDate();
        Map<String, Double> redisJobs = new LinkedHashMap<>();

        for (OAuthInfoEntity oauthInfo : oauthInfoRepo.findAll()) {
            if (!StringUtils.hasText(oauthInfo.getWorkspaceId())) {
                continue;
            }

            AnalyticsProviderCapabilities capabilities = AnalyticsProviderCapabilities.forProvider(oauthInfo.getProvider());
            if (capabilities == null
                    || !supportsScheduledProviderReconcile(capabilities)
                    || !supportsLivePostCollection(capabilities)) {
                continue;
            }

            String dedupeKey = "provider-reconcile:daily:%s:%s:%s:%s".formatted(
                    oauthInfo.getWorkspaceId(),
                    oauthInfo.getProvider().name(),
                    oauthInfo.getProviderUserId(),
                    snapshotDate
            );
            if (analyticsJobRepo.findByDedupeKey(dedupeKey).isPresent()) {
                continue;
            }

            AnalyticsJobEntity job = new AnalyticsJobEntity();
            job.setWorkspaceId(oauthInfo.getWorkspaceId());
            job.setProvider(oauthInfo.getProvider());
            job.setProviderUserId(oauthInfo.getProviderUserId());
            job.setJobType(AnalyticsJobType.PROVIDER_RECONCILE);
            job.setTriggerType(AnalyticsJobTrigger.SCHEDULED_DAILY);
            job.setDedupeKey(dedupeKey);
            job.setDueAt(now);
            job.setStatus(AnalyticsJobStatus.PENDING);

            AnalyticsJobEntity saved = analyticsJobRepo.save(job);
            redisJobs.put(String.valueOf(saved.getId()), (double) now.toInstant().toEpochMilli());
        }

        if (!redisJobs.isEmpty()) {
            postRedisService.addIds("analytics-snapshot-pool", redisJobs);
        }
    }

    @Transactional
    public AnalyticsJobEntity scheduleWebhookRefresh(String workspaceId,
                                                     Provider provider,
                                                     String providerUserId,
                                                     Long postId,
                                                     String providerPostId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
        String dedupeKey = StringUtils.hasText(providerPostId) && postId != null
                ? buildWebhookPostKey(postId, now)
                : buildProviderReconcileKey(workspaceId, provider, providerUserId, now, false);

        Optional<AnalyticsJobEntity> existing = analyticsJobRepo.findByDedupeKey(dedupeKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        AnalyticsJobEntity job = new AnalyticsJobEntity();
        job.setWorkspaceId(workspaceId);
        job.setProvider(provider);
        job.setProviderUserId(providerUserId);
        job.setPostId(postId);
        job.setProviderPostId(providerPostId);
        job.setJobType(postId != null ? AnalyticsJobType.POST_CURRENT : AnalyticsJobType.PROVIDER_RECONCILE);
        job.setTriggerType(AnalyticsJobTrigger.WEBHOOK);
        job.setDedupeKey(dedupeKey);
        job.setDueAt(now);
        job.setStatus(AnalyticsJobStatus.PENDING);

        AnalyticsJobEntity saved = analyticsJobRepo.save(job);
        postRedisService.addIds("analytics-snapshot-pool", Map.of(
                String.valueOf(saved.getId()),
                (double) now.toInstant().toEpochMilli()
        ));
        return saved;
    }

    private AnalyticsFilterOptionsResponse buildFilterOptions(List<ConnectedAccount> connectedAccounts,
                                                             List<PostEntity> analyticsPosts) {
        List<ConnectedAccount> analyticsReadyAccounts = connectedAccounts.stream()
                .filter(account -> isAnalyticsReadyProvider(account.getPlatform().name()))
                .toList();
        List<PostEntity> analyticsReadyPosts = analyticsPosts.stream()
                .filter(post -> isAnalyticsReadyProvider(post.getProvider().name()))
                .toList();

        List<AnalyticsSelectOptionResponse> platformOptions = analyticsReadyAccounts.stream()
                .map(ConnectedAccount::getPlatform)
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .map(platform -> new AnalyticsSelectOptionResponse(platform.name(), labelForPlatform(platform.name())))
                .toList();

        List<AnalyticsSelectOptionResponse> accountOptions = analyticsReadyAccounts.stream()
                .sorted(Comparator.comparing(ConnectedAccount::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(account -> new AnalyticsSelectOptionResponse(
                        account.getProviderUserId(),
                        "%s (%s)".formatted(account.getUsername(), labelForPlatform(account.getPlatform().name()))
                ))
                .toList();

        List<AnalyticsSelectOptionResponse> campaignOptions = analyticsReadyPosts.stream()
                .map(PostEntity::getPostCollection)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        PostCollectionEntity::getId,
                        collection -> new AnalyticsSelectOptionResponse(
                                String.valueOf(collection.getId()),
                                labelForCampaign(collection)
                        ),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();

        List<AnalyticsSelectOptionResponse> contentTypeOptions = analyticsReadyPosts.stream()
                .map(PostEntity::getPostType)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .map(type -> new AnalyticsSelectOptionResponse(type.name(), type.name()))
                .toList();

        return new AnalyticsFilterOptionsResponse(
                platformOptions,
                accountOptions,
                campaignOptions,
                contentTypeOptions
        );
    }

    private AnalyticsShellSummaryResponse buildSummary(String workspaceId,
                                                       AnalyticsSlice slice,
                                                       List<ConnectedAccount> connectedAccounts,
                                                       List<PostEntity> analyticsPosts,
                                                       List<WorkspacePostAnalyticsEntity> currentMetrics,
                                                       List<PostAnalyticsSnapshotEntity> milestoneSnapshots,
                                                       List<AccountAnalyticsSnapshotEntity> accountSnapshots) {
        List<PostEntity> filteredPosts = analyticsPosts.stream()
                .filter(slice::matches)
                .toList();
        Set<Long> filteredPostIds = filteredPosts.stream()
                .map(PostEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> filteredProviderUserIds = filteredPosts.stream()
                .map(PostEntity::getProviderUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int matchingAccounts = (int) connectedAccounts.stream()
                .filter(slice::matches)
                .count();
        int campaignCount = (int) filteredPosts.stream()
                .map(post -> post.getPostCollection().getId())
                .distinct()
                .count();

        List<WorkspacePostAnalyticsEntity> filteredCurrentMetrics = currentMetrics.stream()
                .filter(metric -> filteredPostIds.contains(metric.getPostId()))
                .toList();
        List<PostAnalyticsSnapshotEntity> filteredMilestones = milestoneSnapshots.stream()
                .filter(snapshot -> filteredPostIds.contains(snapshot.getPostId()))
                .toList();
        List<AccountAnalyticsSnapshotEntity> filteredAccountSnapshots = accountSnapshots.stream()
                .filter(snapshot -> filteredProviderUserIds.contains(snapshot.getProviderUserId()))
                .filter(snapshot -> !snapshot.getSnapshotDate().isBefore(slice.fromDate()))
                .toList();

        OffsetDateTime lastAnalyticsAt = latestTimestamp(
                filteredCurrentMetrics.stream().map(WorkspacePostAnalyticsEntity::getLastCollectedAt).toList(),
                filteredMilestones.stream().map(PostAnalyticsSnapshotEntity::getFetchedAt).toList(),
                filteredAccountSnapshots.stream().map(AccountAnalyticsSnapshotEntity::getFetchedAt).toList()
        );

        long pendingJobs = analyticsJobRepo.countByWorkspaceIdAndStatus(workspaceId, AnalyticsJobStatus.PENDING);

        return new AnalyticsShellSummaryResponse(
                matchingAccounts,
                campaignCount,
                filteredPosts.size(),
                filteredCurrentMetrics.size(),
                filteredMilestones.size(),
                filteredAccountSnapshots.size(),
                (int) pendingJobs,
                lastAnalyticsAt,
                !filteredCurrentMetrics.isEmpty() || !filteredMilestones.isEmpty() || !filteredAccountSnapshots.isEmpty()
        );
    }

    private List<AnalyticsProviderCoverageResponse> buildCoverageResponses(String workspaceId,
                                                                          AnalyticsSlice slice,
                                                                          List<ConnectedAccount> connectedAccounts,
                                                                          List<PostEntity> analyticsPosts,
                                                                          List<WorkspacePostAnalyticsEntity> currentMetrics,
                                                                          List<PostAnalyticsSnapshotEntity> milestoneSnapshots,
                                                                          List<AccountAnalyticsSnapshotEntity> accountSnapshots) {
        List<PostEntity> filteredPosts = analyticsPosts.stream()
                .filter(slice::matches)
                .toList();
        Set<Long> filteredPostIds = filteredPosts.stream()
                .map(PostEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> filteredProviderUserIds = filteredPosts.stream()
                .map(PostEntity::getProviderUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ConnectedAccount> filteredAccounts = connectedAccounts.stream()
                .filter(slice::matches)
                .toList();

        Set<Provider> providers = new LinkedHashSet<>();
        filteredAccounts.stream()
                .map(ConnectedAccount::getPlatform)
                .map(platform -> Provider.valueOf(platform.name().toUpperCase()))
                .forEach(providers::add);
        currentMetrics.stream()
                .filter(metric -> filteredPostIds.contains(metric.getPostId()))
                .map(WorkspacePostAnalyticsEntity::getProvider)
                .forEach(providers::add);
        milestoneSnapshots.stream()
                .filter(snapshot -> filteredPostIds.contains(snapshot.getPostId()))
                .map(PostAnalyticsSnapshotEntity::getProvider)
                .filter(StringUtils::hasText)
                .map(Provider::valueOf)
                .forEach(providers::add);
        if (slice.provider() != null) {
            providers.add(slice.provider());
        } else {
            filteredPosts.stream().map(PostEntity::getProvider).forEach(providers::add);
        }

        List<AnalyticsProviderCoverageResponse> responses = new ArrayList<>();
        for (Provider provider : providers) {
            AnalyticsProviderCapabilities capabilities = AnalyticsProviderCapabilities.forProvider(provider);
            AnalyticsProviderCoverageEntity coverage = ensureCoverageRow(workspaceId, provider);

            List<WorkspacePostAnalyticsEntity> currentForProvider = currentMetrics.stream()
                    .filter(metric -> provider.equals(metric.getProvider()))
                    .toList();
            List<PostAnalyticsSnapshotEntity> milestonesForProvider = milestoneSnapshots.stream()
                    .filter(snapshot -> provider.name().equals(snapshot.getProvider()))
                    .toList();
            List<AccountAnalyticsSnapshotEntity> accountForProvider = accountSnapshots.stream()
                    .filter(snapshot -> provider.name().equals(snapshot.getProvider()))
                    .filter(snapshot -> filteredProviderUserIds.contains(snapshot.getProviderUserId()))
                    .toList();

            hydrateCoverageFromObservedData(coverage, capabilities, currentForProvider, milestonesForProvider, accountForProvider);
            analyticsProviderCoverageRepo.save(coverage);

            int connectedAccountCount = (int) filteredAccounts.stream()
                    .filter(account -> provider.name().equals(account.getPlatform().name().toUpperCase()))
                    .count();

            responses.add(new AnalyticsProviderCoverageResponse(
                    provider.name(),
                    connectedAccountCount,
                    coverage.getPostAnalyticsState().name(),
                    coverage.getAccountAnalyticsState().name(),
                    coverage.getFreshnessStatus().name(),
                    currentForProvider.size(),
                    milestonesForProvider.size(),
                    accountForProvider.size(),
                    coverage.getLastPostAnalyticsAt(),
                    coverage.getLastAccountAnalyticsAt(),
                    coverage.getLastSuccessfulJobAt(),
                    coverage.getLastAttemptedJobAt(),
                    coverage.getLastManualRefreshRequestedAt(),
                    buildMetricAvailabilityForCoverage(provider, filteredPosts, currentForProvider),
                    capabilities == null ? List.of() : enumNames(capabilities.supportedPostMetrics()),
                    capabilities == null ? List.of() : enumNames(capabilities.supportedAccountMetrics()),
                    capabilities == null ? List.of() : enumNames(capabilities.collectionModes()),
                    coverage.getLastErrorSummary()
            ));
        }

        responses.sort(Comparator.comparing(AnalyticsProviderCoverageResponse::getProvider));
        return responses;
    }

    private List<AnalyticsMetricAvailabilityWindow> buildMetricAvailabilityForCoverage(Provider provider,
                                                                                       List<PostEntity> filteredPosts,
                                                                                       List<WorkspacePostAnalyticsEntity> currentForProvider) {
        if (!Provider.X.equals(provider)) {
            return List.of();
        }

        List<PostEntity> xPosts = filteredPosts.stream()
                .filter(post -> Provider.X.equals(post.getProvider()))
                .toList();
        if (xPosts.isEmpty()) {
            return List.of();
        }

        int totalPosts = xPosts.size();
        int videoPosts = (int) xPosts.stream().filter(post -> PostType.VIDEO.equals(post.getPostType())).count();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime rollingWindowStart = now.minusDays(30);

        int clickEligiblePosts = 0;
        int clickExpiredPosts = 0;
        for (PostEntity post : xPosts) {
            OffsetDateTime publishedAt = post.getScheduledTime();
            if (publishedAt != null && now.isAfter(publishedAt.plusDays(30))) {
                clickExpiredPosts++;
            } else {
                clickEligiblePosts++;
            }
        }

        List<AnalyticsMetricAvailabilityWindow> availability = new ArrayList<>();
        availability.add(AnalyticsMetricAvailabilityWindow.builder()
                .metrics(List.of("IMPRESSIONS", "LIKES", "COMMENTS", "SHARES"))
                .label("Public post metrics")
                .status("AVAILABLE")
                .source("PUBLIC")
                .totalPostCount(totalPosts)
                .eligiblePostCount(totalPosts)
                .note("Collected for all owned X posts in this workspace slice.")
                .build());

        availability.add(AnalyticsMetricAvailabilityWindow.builder()
                .metrics(List.of("CLICKS"))
                .label("User-context click metrics")
                .status(
                        clickEligiblePosts == totalPosts
                                ? "AVAILABLE"
                                : clickEligiblePosts > 0 ? "PARTIAL" : "WINDOW_EXPIRED"
                )
                .source("USER_CONTEXT")
                .windowStartsAt(rollingWindowStart)
                .eligiblePostCount(clickEligiblePosts)
                .totalPostCount(totalPosts)
                .note(
                        clickEligiblePosts == totalPosts
                                ? "Every X post in this slice is still inside X's 30-day private-metrics window."
                                : clickEligiblePosts > 0
                                ? "%s of %s X posts in this slice can still refresh private click metrics.".formatted(
                                        clickEligiblePosts,
                                        totalPosts
                                )
                                : "All X posts in this slice are outside X's 30-day private-metrics window."
                )
                .build());

        if (videoPosts > 0) {
            long trackedVideoRows = currentForProvider.stream()
                    .filter(row -> Provider.X.equals(row.getProvider()))
                    .filter(row -> PostType.VIDEO.equals(row.getPostType()))
                    .count();

            availability.add(AnalyticsMetricAvailabilityWindow.builder()
                    .metrics(List.of("VIDEO_VIEWS"))
                    .label("Video media metrics")
                    .status("AVAILABLE")
                    .source("MEDIA_PUBLIC")
                    .eligiblePostCount((int) trackedVideoRows)
                    .totalPostCount(videoPosts)
                    .note("Collected from X media expansions for video posts in this slice.")
                    .build());
        }

        return availability;
    }

    private AnalyticsProviderCoverageEntity ensureCoverageRow(String workspaceId, Provider provider) {
        return analyticsProviderCoverageRepo.findByWorkspaceIdAndProvider(workspaceId, provider)
                .orElseGet(() -> {
                    AnalyticsProviderCoverageEntity entity = new AnalyticsProviderCoverageEntity();
                    entity.setWorkspaceId(workspaceId);
                    entity.setProvider(provider);
                    AnalyticsProviderCapabilities capabilities = AnalyticsProviderCapabilities.forProvider(provider);
                    if (capabilities != null) {
                        entity.setPostAnalyticsState(capabilities.defaultPostState());
                        entity.setAccountAnalyticsState(capabilities.defaultAccountState());
                    }
                    return analyticsProviderCoverageRepo.save(entity);
                });
    }

    private void hydrateCoverageFromObservedData(AnalyticsProviderCoverageEntity coverage,
                                                 AnalyticsProviderCapabilities capabilities,
                                                 List<WorkspacePostAnalyticsEntity> currentMetrics,
                                                 List<PostAnalyticsSnapshotEntity> milestoneSnapshots,
                                                 List<AccountAnalyticsSnapshotEntity> accountSnapshots) {
        OffsetDateTime latestPostAnalyticsAt = latestTimestamp(
                currentMetrics.stream().map(WorkspacePostAnalyticsEntity::getLastCollectedAt).toList(),
                milestoneSnapshots.stream().map(PostAnalyticsSnapshotEntity::getFetchedAt).toList()
        );
        OffsetDateTime latestAccountAnalyticsAt = latestTimestamp(
                accountSnapshots.stream().map(AccountAnalyticsSnapshotEntity::getFetchedAt).toList()
        );

        if (latestPostAnalyticsAt != null) {
            coverage.setLastPostAnalyticsAt(latestPostAnalyticsAt);
            coverage.setPostAnalyticsState(capabilities != null && capabilities.defaultPostState() == AnalyticsCoverageState.PARTIAL
                    ? AnalyticsCoverageState.PARTIAL
                    : AnalyticsCoverageState.LIVE);
        } else if (capabilities != null) {
            coverage.setPostAnalyticsState(capabilities.defaultPostState());
        }

        if (latestAccountAnalyticsAt != null) {
            coverage.setLastAccountAnalyticsAt(latestAccountAnalyticsAt);
            coverage.setAccountAnalyticsState(
                    capabilities != null && capabilities.defaultAccountState() == AnalyticsCoverageState.UNSUPPORTED
                            ? AnalyticsCoverageState.UNSUPPORTED
                            : AnalyticsCoverageState.LIVE
            );
        } else if (capabilities != null) {
            coverage.setAccountAnalyticsState(capabilities.defaultAccountState());
        }

        OffsetDateTime latestAny = latestOf(latestPostAnalyticsAt, latestAccountAnalyticsAt);
        coverage.setFreshnessStatus(computeFreshness(latestAny));
    }

    private AnalyticsFreshnessStatus computeFreshness(OffsetDateTime latestAny) {
        if (latestAny == null) {
            return AnalyticsFreshnessStatus.NO_DATA;
        }
        long hours = ChronoUnit.HOURS.between(latestAny, OffsetDateTime.now(ZoneOffset.UTC));
        if (hours <= 12) {
            return AnalyticsFreshnessStatus.FRESH;
        }
        if (hours <= 48) {
            return AnalyticsFreshnessStatus.DELAYED;
        }
        return AnalyticsFreshnessStatus.STALE;
    }

    private OffsetDateTime latestTimestamp(List<OffsetDateTime>... timestampLists) {
        return List.of(timestampLists).stream()
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private OffsetDateTime latestOf(OffsetDateTime... timestamps) {
        OffsetDateTime latest = null;
        for (OffsetDateTime timestamp : timestamps) {
            if (timestamp == null) {
                continue;
            }
            if (latest == null || timestamp.isAfter(latest)) {
                latest = timestamp;
            }
        }
        return latest;
    }

    private List<String> enumNames(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }

    private OffsetDateTime nextManualRefreshAt(OffsetDateTime lastManualRefreshRequestedAt) {
        if (lastManualRefreshRequestedAt == null) {
            return null;
        }
        return lastManualRefreshRequestedAt.plusMinutes(MANUAL_REFRESH_COOLDOWN_MINUTES);
    }

    private boolean supportsLivePostCollection(AnalyticsProviderCapabilities capabilities) {
        return capabilities != null && capabilities.defaultPostState() != AnalyticsCoverageState.PLANNED;
    }

    private boolean supportsLiveAccountCollection(AnalyticsProviderCapabilities capabilities) {
        return capabilities != null && capabilities.defaultAccountState() == AnalyticsCoverageState.LIVE;
    }

    private boolean supportsScheduledProviderReconcile(AnalyticsProviderCapabilities capabilities) {
        return capabilities != null && (
                capabilities.collectionModes().contains(AnalyticsCollectionMode.WEBHOOK_REFRESH)
                        || capabilities.collectionModes().contains(AnalyticsCollectionMode.SCHEDULED_RECONCILE)
        );
    }

    private boolean isAnalyticsReadyProvider(String providerName) {
        AnalyticsProviderCapabilities capabilities = AnalyticsProviderCapabilities.forProvider(
                Provider.valueOf(providerName.toUpperCase())
        );
        return capabilities != null && (
                supportsLivePostCollection(capabilities) || supportsLiveAccountCollection(capabilities)
        );
    }

    private String labelForCampaign(PostCollectionEntity collection) {
        String description = collection.getDescription();
        if (!StringUtils.hasText(description)) {
            return "Campaign #" + collection.getId();
        }
        String normalized = description.strip();
        if (normalized.length() <= 48) {
            return normalized;
        }
        return normalized.substring(0, 45) + "...";
    }

    private String labelForPlatform(String platform) {
        return switch (platform.toLowerCase()) {
            case "x" -> "X";
            case "youtube" -> "YouTube";
            case "linkedin" -> "LinkedIn";
            case "instagram" -> "Instagram";
            case "facebook" -> "Facebook";
            case "threads" -> "Threads";
            case "tiktok" -> "TikTok";
            default -> platform;
        };
    }

    private String normalizeProvider(String platform) {
        if (!StringUtils.hasText(platform)) {
            return "ALL";
        }
        return platform.toUpperCase();
    }

    private String buildProviderReconcileKey(String workspaceId,
                                             Provider provider,
                                             String providerUserId,
                                             OffsetDateTime at,
                                             boolean manual) {
        long bucket = at.toEpochSecond() / (MANUAL_REFRESH_COOLDOWN_MINUTES * 60L);
        String trigger = manual ? "manual" : "webhook";
        return "provider-reconcile:%s:%s:%s:%s:%s".formatted(
                trigger,
                workspaceId,
                provider.name(),
                providerUserId,
                bucket
        );
    }

    private String buildWebhookPostKey(Long postId, OffsetDateTime at) {
        long bucket = at.toEpochSecond() / (5 * 60L);
        return "post-current:webhook:%s:%s".formatted(postId, bucket);
    }

    private String requireWorkspaceId() {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        if (!StringUtils.hasText(workspaceId)) {
            throw new IllegalStateException("Workspace context is required for analytics.");
        }
        return workspaceId;
    }

    private record AnalyticsSlice(int days,
                                  Provider provider,
                                  String providerUserId,
                                  Long campaignId,
                                  PostType postType,
                                  OffsetDateTime fromDateTime,
                                  LocalDate fromDate) {

        private static AnalyticsSlice of(int days,
                                         String platform,
                                         String providerUserId,
                                         Long campaignId,
                                         String contentType) {
            int normalizedDays = Math.max(days, 1);
            Provider provider = StringUtils.hasText(platform)
                    ? Provider.valueOf(platform.toUpperCase())
                    : null;
            PostType postType = StringUtils.hasText(contentType)
                    ? PostType.valueOf(contentType.toUpperCase())
                    : null;
            OffsetDateTime fromDateTime = OffsetDateTime.now(ZoneOffset.UTC).minusDays(normalizedDays);
            return new AnalyticsSlice(
                    normalizedDays,
                    provider,
                    StringUtils.hasText(providerUserId) ? providerUserId : null,
                    campaignId,
                    postType,
                    fromDateTime,
                    fromDateTime.toLocalDate()
            );
        }

        private boolean matches(PostEntity post) {
            if (provider != null && !provider.equals(post.getProvider())) {
                return false;
            }
            if (StringUtils.hasText(providerUserId) && !providerUserId.equals(post.getProviderUserId())) {
                return false;
            }
            if (campaignId != null && !campaignId.equals(post.getPostCollection().getId())) {
                return false;
            }
            if (postType != null && !postType.equals(post.getPostType())) {
                return false;
            }
            OffsetDateTime publishedAt = post.getScheduledTime();
            return publishedAt == null || !publishedAt.isBefore(fromDateTime);
        }

        private boolean matches(ConnectedAccount account) {
            if (provider != null && !provider.name().equals(account.getPlatform().name().toUpperCase())) {
                return false;
            }
            return !StringUtils.hasText(providerUserId) || providerUserId.equals(account.getProviderUserId());
        }
    }
}
