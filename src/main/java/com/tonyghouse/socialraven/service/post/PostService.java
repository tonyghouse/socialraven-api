package com.tonyghouse.socialraven.service.post;

import com.tonyghouse.socialraven.constant.PostCollectionType;
import com.tonyghouse.socialraven.constant.PostActorType;
import com.tonyghouse.socialraven.constant.PostApprovalStage;
import com.tonyghouse.socialraven.constant.PostCollectionVersionEvent;
import com.tonyghouse.socialraven.constant.PostReviewAction;
import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.constant.PostStatus;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.RecoveryState;
import com.tonyghouse.socialraven.constant.WorkspaceApprovalMode;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.dto.CalendarPostResponse;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.dto.MediaResponse;
import com.tonyghouse.socialraven.dto.PostCollectionActivityTimelineEntryResponse;
import com.tonyghouse.socialraven.dto.PostCollection;
import com.tonyghouse.socialraven.dto.PostCollectionApprovalDiffResponse;
import com.tonyghouse.socialraven.dto.PostCollectionReviewActionRequest;
import com.tonyghouse.socialraven.dto.PostCollectionReviewHistoryResponse;
import com.tonyghouse.socialraven.dto.PostCollectionResponse;
import com.tonyghouse.socialraven.dto.PostCollectionVersionResponse;
import com.tonyghouse.socialraven.dto.PostAnalyticsSummaryResponse;
import com.tonyghouse.socialraven.dto.PostMedia;
import com.tonyghouse.socialraven.dto.PostResponse;
import com.tonyghouse.socialraven.dto.ScheduleDraftRequest;
import com.tonyghouse.socialraven.dto.UpdatePostCollectionRequest;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostCollectionReviewHistoryEntity;
import com.tonyghouse.socialraven.entity.PostCollectionVersionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.PostMediaEntity;
import com.tonyghouse.socialraven.entity.WorkspacePostAnalyticsEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.helper.PostPoolHelper;
import com.tonyghouse.socialraven.mapper.PostTypeMapper;
import com.tonyghouse.socialraven.mapper.ProviderPlatformMapper;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.PostCollectionReviewHistoryRepo;
import com.tonyghouse.socialraven.repo.PostMediaRepo;
import com.tonyghouse.socialraven.repo.PostRepo;
import com.tonyghouse.socialraven.repo.WorkspacePostAnalyticsRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import com.tonyghouse.socialraven.service.storage.StorageService;
import com.tonyghouse.socialraven.service.workspace.WorkspaceApprovalRuleService;
import com.tonyghouse.socialraven.service.workspace.WorkspaceCapabilityService;
import com.tonyghouse.socialraven.util.SecurityContextUtil;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;


@Service
@Slf4j
public class PostService {
    private static final long TIMELINE_MATCH_WINDOW_MILLIS = 5_000L;
    private static final Set<PostCollectionVersionEvent> MIRRORED_WORKFLOW_VERSION_EVENTS = EnumSet.of(
            PostCollectionVersionEvent.SUBMITTED,
            PostCollectionVersionEvent.RESUBMITTED,
            PostCollectionVersionEvent.STEP_APPROVED,
            PostCollectionVersionEvent.APPROVED,
            PostCollectionVersionEvent.CHANGES_REQUESTED,
            PostCollectionVersionEvent.REAPPROVAL_REQUIRED
    );
    private static final DateTimeFormatter AUDIT_EXPORT_TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Autowired
    private PostCollectionRepo postCollectionRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private PostMediaRepo postMediaRepo;

    @Autowired
    private PostCollectionReviewHistoryRepo postCollectionReviewHistoryRepo;

    @Autowired
    private WorkspacePostAnalyticsRepo workspacePostAnalyticsRepo;

    @Autowired
    private StorageService storageService;

    @Autowired
    private AccountProfileService accountProfileService;

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClerkUserService clerkUserService;

    @Autowired
    private WorkspaceCapabilityService workspaceCapabilityService;

    @Autowired
    private WorkspaceApprovalRuleService workspaceApprovalRuleService;

    @Autowired
    private PostCollectionVersionService postCollectionVersionService;

    @Value("${socialraven.approval.reminder.initial-delay-hours:12}")
    private long initialApprovalReminderDelayHours;


    @Transactional
    public PostCollectionResponse schedulePostCollection(PostCollection postCollectionReq) {
        boolean isDraft = postCollectionReq.isDraft();
        List<ConnectedAccount> connectedAccounts = postCollectionReq.getConnectedAccounts();
        WorkspaceRole callerRole = WorkspaceContext.getRole();

        if (!isDraft && CollectionUtils.isEmpty(connectedAccounts)) {
            throw new SocialRavenException("Select connected accounts for post collection", HttpStatus.BAD_REQUEST);
        }
        if (!isDraft && postCollectionReq.getScheduledTime() == null) {
            throw new SocialRavenException("scheduledTime is required", HttpStatus.BAD_REQUEST);
        }

        PostCollectionEntity postCollection = new PostCollectionEntity();

        String userId = SecurityContextUtil.getUserId(SecurityContextHolder.getContext());
        String workspaceId = WorkspaceContext.getWorkspaceId();
        assertCanManageCampaignApprovalOverride(
                workspaceId,
                userId,
                callerRole,
                postCollectionReq.getApprovalModeOverride() != null
        );

        postCollection.setDraft(true);
        postCollection.setReviewStatus(PostReviewStatus.DRAFT);
        PostCollectionType postType = postCollectionReq.getPostType();
        postCollection.setPostCollectionType(postType);
        postCollection.setCreatedBy(userId);
        postCollection.setWorkspaceId(workspaceId);
        postCollection.setDescription(postCollectionReq.getDescription() != null ? postCollectionReq.getDescription() : "");
        OffsetDateTime scheduledTime = postCollectionReq.getScheduledTime();
        postCollection.setScheduledTime(scheduledTime);
        postCollection.setApprovalModeOverride(postCollectionReq.getApprovalModeOverride());

        if (postCollectionReq.getPlatformConfigs() != null) {
            try {
                postCollection.setPlatformConfigs(objectMapper.writeValueAsString(postCollectionReq.getPlatformConfigs()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize platformConfigs", e);
            }
        }

        List<PostMedia> media = postCollectionReq.getMedia() != null ? postCollectionReq.getMedia() : Collections.emptyList();
        List<PostMediaEntity> postMediaEntities = new ArrayList<>();
        for (var postMediaDto : media) {
            PostMediaEntity postMediaEntity = new PostMediaEntity();
            postMediaEntity.setFileKey(postMediaDto.getFileKey());
            postMediaEntity.setSize(postMediaDto.getSize());
            postMediaEntity.setFileName(postMediaDto.getFileName());
            postMediaEntity.setMimeType(postMediaDto.getMimeType());
            postMediaEntity.setPostCollection(postCollection);
            postMediaEntities.add(postMediaEntity);
        }
        postCollection.setMediaFiles(postMediaEntities);

        List<PostEntity> postEntities = new ArrayList<>();
        if (!CollectionUtils.isEmpty(connectedAccounts)) {
            for (ConnectedAccount connectedAccount : connectedAccounts) {
                PostEntity post = new PostEntity();
                post.setProvider(ProviderPlatformMapper.getProviderByPlatform(connectedAccount.getPlatform()));
                post.setProviderUserId(connectedAccount.getProviderUserId());
                post.setPostCollection(postCollection);
                post.setPostStatus(PostStatus.DRAFT);
                post.setPostType(PostTypeMapper.getPostTypeByPostCollectionType(postType));
                post.setScheduledTime(scheduledTime);
                postEntities.add(post);
            }
        }
        postCollection.setPosts(postEntities);

        PostCollectionEntity savedPost = postCollectionRepo.save(postCollection);
        postCollectionVersionService.recordVersion(savedPost, PostCollectionVersionEvent.CREATED, userId);

        if (!isDraft) {
            WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot =
                    workspaceApprovalRuleService.getSnapshot(workspaceId);
            SubmissionDecision submissionDecision = resolveSubmissionDecision(
                    approvalRuleSnapshot,
                    savedPost,
                    workspaceId,
                    userId,
                    callerRole
            );
            if (submissionDecision.requiresReview()) {
                transitionToInReview(savedPost, userId, null, submissionDecision.requiredApprovalSteps());
                log.info(
                        "Collection submitted for review: collectionId={}, workspaceId={}, mode={}, steps={}",
                        savedPost.getId(),
                        workspaceId,
                        submissionDecision.approvalMode(),
                        submissionDecision.requiredApprovalSteps()
                );
            } else {
                transitionToScheduledWithoutReview(savedPost);
                enqueueScheduledPosts(savedPost.getPosts());
                log.info(
                        "Collection scheduled without review: collectionId={}, workspaceId={}, mode={}",
                        savedPost.getId(),
                        workspaceId,
                        submissionDecision.approvalMode()
                );
            }
            savedPost = postCollectionRepo.save(savedPost);
            postCollectionVersionService.recordVersion(
                    savedPost,
                    submissionDecision.requiresReview()
                            ? PostCollectionVersionEvent.SUBMITTED
                            : PostCollectionVersionEvent.SCHEDULED_DIRECT,
                    userId
            );
        } else {
            log.info("Draft saved: collectionId={}, workspaceId={}", savedPost.getId(), workspaceId);
        }

        return buildResponse(savedPost, workspaceId, false);
    }

    @Transactional
    public PostCollectionResponse scheduleDraftCollection(String userId, Long collectionId, ScheduleDraftRequest req) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole callerRole = WorkspaceContext.getRole();
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException(
                        "Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (!collection.isDraft()) {
            throw new SocialRavenException("Collection is not a draft", HttpStatus.BAD_REQUEST);
        }
        if (CollectionUtils.isEmpty(collection.getPosts())) {
            throw new SocialRavenException(
                    "Select at least one connected account before scheduling", HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime scheduledTime = req.getScheduledTime();
        collection.setScheduledTime(scheduledTime);
        assertCanManageCampaignApprovalOverride(
                workspaceId,
                userId,
                callerRole,
                req.getApprovalModeOverride() != null || Boolean.TRUE.equals(req.getClearApprovalModeOverride())
        );
        if (Boolean.TRUE.equals(req.getClearApprovalModeOverride())) {
            collection.setApprovalModeOverride(null);
        } else if (req.getApprovalModeOverride() != null) {
            collection.setApprovalModeOverride(req.getApprovalModeOverride());
        }

        WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot =
                workspaceApprovalRuleService.getSnapshot(workspaceId);
        SubmissionDecision submissionDecision = resolveSubmissionDecision(
                approvalRuleSnapshot,
                collection,
                workspaceId,
                userId,
                callerRole
        );
        PostReviewStatus previousStatus = collection.getReviewStatus() != null
                ? collection.getReviewStatus()
                : PostReviewStatus.DRAFT;
        if (submissionDecision.requiresReview()) {
            transitionToInReview(collection, userId, null, submissionDecision.requiredApprovalSteps());
        } else {
            transitionToScheduledWithoutReview(collection);
            enqueueScheduledPosts(collection.getPosts());
        }

        PostCollectionEntity saved = postCollectionRepo.save(collection);
        postCollectionVersionService.recordVersion(
                saved,
                submissionDecision.requiresReview()
                        ? previousStatus == PostReviewStatus.CHANGES_REQUESTED
                                ? PostCollectionVersionEvent.RESUBMITTED
                                : PostCollectionVersionEvent.SUBMITTED
                        : PostCollectionVersionEvent.SCHEDULED_DIRECT,
                userId
        );
        return buildResponse(saved, workspaceId, true);
    }

    @Transactional
    public void deletePostCollection(String userId, Long collectionId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException(
                        "Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        List<PostEntity> posts = collection.getPosts();
        if (posts != null && !posts.isEmpty()) {
            try (Jedis jedis = jedisPool.getResource()) {
                String[] postIds = posts.stream()
                        .map(p -> p.getId().toString())
                        .toArray(String[]::new);
                jedis.zrem(PostPoolHelper.getPostsPoolName(), postIds);
            }
        }
        postCollectionRepo.delete(collection);
        log.info("Deleted post collection id={} for workspaceId={}", collectionId, workspaceId);
    }

    @Transactional
    public PostCollectionResponse createRecoveryDraft(String userId, Long collectionId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity failedCollection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException(
                        "Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(failedCollection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (failedCollection.isDraft()) {
            throw new SocialRavenException("Draft collections cannot create a recovery draft", HttpStatus.BAD_REQUEST);
        }
        if (!hasRecoverableFailedPosts(failedCollection.getPosts())) {
            throw new SocialRavenException(
                    "Recovery drafts are only available when one or more channels in the collection have failed",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (failedCollection.getFailureState() != RecoveryState.RECOVERY_REQUIRED
                && failedCollection.getRecoveryCollectionId() == null) {
            throw new SocialRavenException(
                    "Recovery draft is not available for this collection yet",
                    HttpStatus.BAD_REQUEST
            );
        }

        PostCollectionEntity existingRecovery = loadExistingRecoveryCollection(failedCollection, workspaceId);
        if (existingRecovery != null) {
            markFailedCollectionHandled(failedCollection, userId, existingRecovery.getId());
            return buildResponse(existingRecovery, workspaceId, true);
        }

        PostCollectionEntity recoveryDraft = cloneAsRecoveryDraft(failedCollection, userId, workspaceId);
        PostCollectionEntity savedRecoveryDraft = postCollectionRepo.save(recoveryDraft);
        postCollectionVersionService.recordVersion(savedRecoveryDraft, PostCollectionVersionEvent.RECOVERY_CREATED, userId);

        markFailedCollectionHandled(failedCollection, userId, savedRecoveryDraft.getId());

        return buildResponse(savedRecoveryDraft, workspaceId, true);
    }

    @Transactional
    public PostCollectionResponse updatePostCollection(String userId, Long collectionId, UpdatePostCollectionRequest req) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException(
                        "Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (collection.isDraft() && collection.getReviewStatus() == PostReviewStatus.IN_REVIEW) {
            throw new SocialRavenException(
                    "This collection is currently in review. Request changes before editing it.",
                    HttpStatus.CONFLICT
            );
        }
        MaterialState beforeState = captureMaterialState(collection);
        List<String> removedPostRedisKeys = new ArrayList<>();
        List<String> newlyAddedProviderUserIds = new ArrayList<>();

        if (req.getDescription() != null) {
            collection.setDescription(req.getDescription());
        }

        assertCanManageCampaignApprovalOverride(
                workspaceId,
                userId,
                WorkspaceContext.getRole(),
                req.getApprovalModeOverride() != null || Boolean.TRUE.equals(req.getClearApprovalModeOverride())
        );
        if (Boolean.TRUE.equals(req.getClearApprovalModeOverride())) {
            collection.setApprovalModeOverride(null);
        } else if (req.getApprovalModeOverride() != null) {
            collection.setApprovalModeOverride(req.getApprovalModeOverride());
        }

        final OffsetDateTime scheduledTime = req.getScheduledTime() != null
                ? req.getScheduledTime()
                : collection.getScheduledTime();
        if (req.getScheduledTime() != null) {
            collection.setScheduledTime(scheduledTime);
        }

        if (req.getPlatformConfigs() != null) {
            try {
                collection.setPlatformConfigs(objectMapper.writeValueAsString(req.getPlatformConfigs()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize platformConfigs", e);
            }
        }

        if (req.getKeepMediaKeys() != null || req.getNewMedia() != null) {
            List<String> keepKeys = req.getKeepMediaKeys() != null ? req.getKeepMediaKeys() : List.of();
            List<PostMediaEntity> updatedMedia = new ArrayList<>();

            if (collection.getMediaFiles() == null) {
                collection.setMediaFiles(new ArrayList<>());
            }
            if (collection.getMediaFiles() != null) {
                updatedMedia.addAll(
                        collection.getMediaFiles().stream()
                                .filter(m -> keepKeys.contains(m.getFileKey()))
                                .collect(Collectors.toList())
                );
            }

            if (req.getNewMedia() != null) {
                for (PostMedia dto : req.getNewMedia()) {
                    PostMediaEntity entity = new PostMediaEntity();
                    entity.setFileKey(dto.getFileKey());
                    entity.setFileName(dto.getFileName());
                    entity.setMimeType(dto.getMimeType());
                    entity.setSize(dto.getSize());
                    entity.setPostCollection(collection);
                    updatedMedia.add(entity);
                }
            }

            collection.getMediaFiles().clear();
            collection.getMediaFiles().addAll(updatedMedia);
        }

        if (req.getConnectedAccounts() != null) {
            List<ConnectedAccount> requestedAccounts = req.getConnectedAccounts();
            if (collection.getPosts() == null) {
                collection.setPosts(new ArrayList<>());
            }

            Set<String> requestedIds = requestedAccounts.stream()
                    .map(ConnectedAccount::getProviderUserId)
                    .collect(Collectors.toSet());
            Set<String> currentIds = collection.getPosts() != null
                    ? collection.getPosts().stream().map(PostEntity::getProviderUserId).collect(Collectors.toSet())
                    : Set.of();

            Iterator<PostEntity> iter = collection.getPosts().iterator();
            while (iter.hasNext()) {
                PostEntity post = iter.next();
                if (!requestedIds.contains(post.getProviderUserId())) {
                    if (post.getPostStatus() == PostStatus.SCHEDULED && post.getId() != null) {
                        removedPostRedisKeys.add(post.getId().toString());
                    }
                    iter.remove();
                }
            }

            PostCollectionType postType = collection.getPostCollectionType();
            boolean collectionIsDraft = collection.isDraft();
            for (ConnectedAccount account : requestedAccounts) {
                if (!currentIds.contains(account.getProviderUserId())) {
                    PostEntity newPost = new PostEntity();
                    newPost.setProvider(ProviderPlatformMapper.getProviderByPlatform(account.getPlatform()));
                    newPost.setProviderUserId(account.getProviderUserId());
                    newPost.setPostCollection(collection);
                    newPost.setPostStatus(collectionIsDraft ? PostStatus.DRAFT : PostStatus.SCHEDULED);
                    newPost.setPostType(PostTypeMapper.getPostTypeByPostCollectionType(postType));
                    newPost.setScheduledTime(scheduledTime);
                    collection.getPosts().add(newPost);
                    if (!collectionIsDraft) {
                        newlyAddedProviderUserIds.add(account.getProviderUserId());
                    }
                }
            }
        }

        if (req.getScheduledTime() != null && collection.getPosts() != null) {
            for (PostEntity post : collection.getPosts()) {
                post.setScheduledTime(scheduledTime);
            }
        }

        MaterialState afterState = captureMaterialState(collection);
        List<String> changedFields = describeMaterialChanges(beforeState, afterState);
        boolean materialChangesDetected = !changedFields.isEmpty();
        boolean requiresReapproval = !collection.isDraft()
                && collection.getReviewStatus() == PostReviewStatus.APPROVED
                && collection.isApprovalLocked()
                && materialChangesDetected;

        if (requiresReapproval && !Boolean.TRUE.equals(req.getAcknowledgeApprovalLock())) {
            throw new SocialRavenException(
                    "This collection is approval-locked. Confirm the edit to move it back into review.",
                    HttpStatus.CONFLICT
            );
        }

        if (requiresReapproval) {
            List<String> scheduledRedisKeys = new ArrayList<>(removedPostRedisKeys);
            scheduledRedisKeys.addAll(collectScheduledRedisKeys(collection.getPosts()));
            removePostIdsFromRedis(scheduledRedisKeys);

            transitionToReapprovalRequired(
                    collection,
                    userId,
                    summarizeMaterialChanges(changedFields),
                    resolveReapprovalStepCount(
                            workspaceApprovalRuleService.getSnapshot(workspaceId),
                            collection
                    )
            );

            PostCollectionEntity saved = postCollectionRepo.save(collection);
            postCollectionVersionService.recordVersion(saved, PostCollectionVersionEvent.REAPPROVAL_REQUIRED, userId);
            return buildResponse(saved, workspaceId, true);
        }

        PostCollectionEntity saved = postCollectionRepo.save(collection);

        removePostIdsFromRedis(removedPostRedisKeys);

        if (!saved.isDraft() && (req.getScheduledTime() != null || !newlyAddedProviderUserIds.isEmpty())) {
            enqueueScheduledPosts(saved.getPosts());
        }

        if (materialChangesDetected) {
            postCollectionVersionService.recordVersion(saved, PostCollectionVersionEvent.UPDATED, userId);
        }

        return buildResponse(saved, workspaceId, true);
    }

    @Transactional
    public PostCollectionResponse approvePostCollection(String userId,
                                                        Long collectionId,
                                                        PostCollectionReviewActionRequest request) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot =
                workspaceApprovalRuleService.getSnapshot(workspaceId);
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException("Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (!collection.isDraft() || collection.getReviewStatus() != PostReviewStatus.IN_REVIEW) {
            throw new SocialRavenException("Only in-review collections can be approved", HttpStatus.BAD_REQUEST);
        }

        WorkspaceRole callerRole = WorkspaceContext.getRole();
        assertCanApprove(workspaceId, userId, callerRole, collection);

        if (collection.getNextApprovalStage() == PostApprovalStage.APPROVER
                && collection.getRequiredApprovalSteps() > 1) {
            transitionToOwnerFinalReview(collection, userId, request != null ? request.getNote() : null);
        } else {
            if (approvalRuleSnapshot.autoScheduleAfterApproval()) {
                transitionToApprovedAndScheduled(collection, userId, request != null ? request.getNote() : null);
                enqueueScheduledPosts(collection.getPosts());
            } else {
                transitionToApprovedAwaitingSchedule(collection, userId, request != null ? request.getNote() : null);
            }
        }

        PostCollectionEntity saved = postCollectionRepo.save(collection);
        PostCollectionVersionEvent versionEvent;
        if (saved.getReviewStatus() == PostReviewStatus.APPROVED) {
            PostCollectionVersionEntity approvedVersion = postCollectionVersionService.recordVersion(
                    saved,
                    PostCollectionVersionEvent.APPROVED,
                    userId
            );
            saved.setLastApprovedVersionId(approvedVersion != null ? approvedVersion.getId() : null);
            saved = postCollectionRepo.save(saved);
            versionEvent = PostCollectionVersionEvent.APPROVED;
        } else {
            postCollectionVersionService.recordVersion(saved, PostCollectionVersionEvent.STEP_APPROVED, userId);
            versionEvent = PostCollectionVersionEvent.STEP_APPROVED;
        }
        log.info("Recorded post-collection version event {} for collectionId={}", versionEvent, saved.getId());
        return buildResponse(saved, workspaceId, true);
    }

    @Transactional
    public PostCollectionResponse requestChanges(String userId,
                                                 Long collectionId,
                                                 PostCollectionReviewActionRequest request) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException("Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (!collection.isDraft() || collection.getReviewStatus() != PostReviewStatus.IN_REVIEW) {
            throw new SocialRavenException("Only in-review collections can request changes", HttpStatus.BAD_REQUEST);
        }

        WorkspaceRole callerRole = WorkspaceContext.getRole();
        assertCanRequestChanges(workspaceId, userId, callerRole);
        transitionToChangesRequested(collection, userId, request != null ? request.getNote() : null);
        PostCollectionEntity saved = postCollectionRepo.save(collection);
        postCollectionVersionService.recordVersion(saved, PostCollectionVersionEvent.CHANGES_REQUESTED, userId);
        return buildResponse(saved, workspaceId, true);
    }

    @Transactional
    PostCollectionEntity approvePostCollectionFromClientReviewer(PostCollectionEntity collection,
                                                                 String reviewerDisplayName,
                                                                 String reviewerEmail,
                                                                 String note) {
        if (collection == null) {
            throw new SocialRavenException("Post collection not found", HttpStatus.NOT_FOUND);
        }
        if (!collection.isDraft() || collection.getReviewStatus() != PostReviewStatus.IN_REVIEW) {
            throw new SocialRavenException("Only in-review collections can be approved", HttpStatus.BAD_REQUEST);
        }
        if (collection.getNextApprovalStage() == PostApprovalStage.OWNER_FINAL) {
            throw new SocialRavenException(
                    "This collection is already awaiting final internal sign-off",
                    HttpStatus.CONFLICT
            );
        }
        WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot =
                workspaceApprovalRuleService.getSnapshot(collection.getWorkspaceId());

        if (collection.getNextApprovalStage() == PostApprovalStage.APPROVER
                && collection.getRequiredApprovalSteps() > 1) {
            transitionToOwnerFinalReview(
                    collection,
                    null,
                    reviewerDisplayName,
                    reviewerEmail,
                    PostActorType.CLIENT_REVIEWER,
                    note
            );
        } else {
            if (approvalRuleSnapshot.autoScheduleAfterApproval()) {
                transitionToApprovedAndScheduled(
                        collection,
                        null,
                        reviewerDisplayName,
                        reviewerEmail,
                        PostActorType.CLIENT_REVIEWER,
                        note
                );
                enqueueScheduledPosts(collection.getPosts());
            } else {
                transitionToApprovedAwaitingSchedule(
                        collection,
                        null,
                        reviewerDisplayName,
                        reviewerEmail,
                        PostActorType.CLIENT_REVIEWER,
                        note
                );
            }
        }

        PostCollectionEntity saved = postCollectionRepo.save(collection);
        if (saved.getReviewStatus() == PostReviewStatus.APPROVED) {
            PostCollectionVersionEntity approvedVersion = postCollectionVersionService.recordVersion(
                    saved,
                    PostCollectionVersionEvent.APPROVED,
                    null,
                    reviewerDisplayName,
                    reviewerEmail,
                    PostActorType.CLIENT_REVIEWER
            );
            saved.setLastApprovedVersionId(approvedVersion != null ? approvedVersion.getId() : null);
            saved = postCollectionRepo.save(saved);
        } else {
            postCollectionVersionService.recordVersion(
                    saved,
                    PostCollectionVersionEvent.STEP_APPROVED,
                    null,
                    reviewerDisplayName,
                    reviewerEmail,
                    PostActorType.CLIENT_REVIEWER
            );
        }
        return saved;
    }

    @Transactional
    PostCollectionEntity requestChangesFromClientReviewer(PostCollectionEntity collection,
                                                          String reviewerDisplayName,
                                                          String reviewerEmail,
                                                          String note) {
        if (collection == null) {
            throw new SocialRavenException("Post collection not found", HttpStatus.NOT_FOUND);
        }
        if (!collection.isDraft() || collection.getReviewStatus() != PostReviewStatus.IN_REVIEW) {
            throw new SocialRavenException("Only in-review collections can request changes", HttpStatus.BAD_REQUEST);
        }

        transitionToChangesRequested(
                collection,
                null,
                reviewerDisplayName,
                reviewerEmail,
                PostActorType.CLIENT_REVIEWER,
                note
        );
        PostCollectionEntity saved = postCollectionRepo.save(collection);
        postCollectionVersionService.recordVersion(
                saved,
                PostCollectionVersionEvent.CHANGES_REQUESTED,
                null,
                reviewerDisplayName,
                reviewerEmail,
                PostActorType.CLIENT_REVIEWER
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public PostCollectionResponse getPostCollectionById(String userId, Long id) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = postCollectionRepo.findById(id)
                .orElseThrow(() -> new SocialRavenException(
                        "Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        return buildResponse(collection, workspaceId, true);
    }

    @Transactional
    public PostCollectionResponse activateApprovedSchedule(String userId, Long collectionId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole callerRole = WorkspaceContext.getRole();
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException("Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (!collection.isDraft()
                || collection.getReviewStatus() != PostReviewStatus.APPROVED
                || !collection.isApprovalLocked()) {
            throw new SocialRavenException(
                    "Only approved collections awaiting scheduling can be activated",
                    HttpStatus.BAD_REQUEST
            );
        }

        assertCanPublish(workspaceId, userId, callerRole);
        transitionApprovedCollectionToScheduled(collection);
        PostCollectionEntity saved = postCollectionRepo.save(collection);
        enqueueScheduledPosts(saved.getPosts());
        postCollectionVersionService.recordVersion(saved, PostCollectionVersionEvent.SCHEDULED_AFTER_APPROVAL, userId);
        return buildResponse(saved, workspaceId, true);
    }

    @Transactional(readOnly = true)
    public byte[] exportApprovalLog(String userId, Long collectionId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException("Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }

        PostCollectionResponse response = buildResponse(collection, workspaceId, true);
        List<PostCollectionActivityTimelineEntryResponse> timeline = response.getActivityTimeline() != null
                ? new ArrayList<>(response.getActivityTimeline())
                : new ArrayList<>();
        timeline.sort((left, right) -> {
            int createdAtCompare = compareCreatedAt(left.getCreatedAt(), right.getCreatedAt());
            if (createdAtCompare != 0) {
                return createdAtCompare;
            }
            return String.valueOf(left.getEventKey()).compareTo(String.valueOf(right.getEventKey()));
        });

        StringBuilder csv = new StringBuilder();
        csv.append("Occurred At (UTC),Category,Event,Actor,Actor Type,From Status,To Status,Version,Scheduled Time (UTC),Note\n");
        for (PostCollectionActivityTimelineEntryResponse entry : timeline) {
            csv.append(csvCell(formatAuditTimestamp(entry.getCreatedAt()))).append(',')
                    .append(csvCell(entry.getCategory())).append(',')
                    .append(csvCell(entry.getLabel())).append(',')
                    .append(csvCell(entry.getActorDisplayName())).append(',')
                    .append(csvCell(entry.getActorType())).append(',')
                    .append(csvCell(entry.getFromStatus())).append(',')
                    .append(csvCell(entry.getToStatus())).append(',')
                    .append(csvCell(entry.getVersionNumber() != null ? String.valueOf(entry.getVersionNumber()) : null)).append(',')
                    .append(csvCell(formatAuditTimestamp(entry.getScheduledTime()))).append(',')
                    .append(csvCell(entry.getNote()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public Page<PostCollectionResponse> getUserPostCollections(
            String userId, int page, String type, String search, List<String> providerUserIds,
            String platform, String sortDir, String dateRange) {
        String workspaceId = WorkspaceContext.getWorkspaceId();

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by("scheduledTime").ascending()
                : Sort.by("scheduledTime").descending();
        Pageable pageable = PageRequest.of(page, 12, sort);

        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(workspaceId, false);
        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));

        String searchPattern = (search != null && !search.trim().isEmpty())
                ? "%" + search.trim().toLowerCase() + "%"
                : null;
        boolean hasAccountFilter = !CollectionUtils.isEmpty(providerUserIds);

        String platformStr = null;
        if (platform != null && !platform.isBlank()) {
            try {
                platformStr = Provider.valueOf(platform.toUpperCase()).name();
            } catch (IllegalArgumentException ignored) {}
        }
        boolean hasPlatformFilter = platformStr != null;

        OffsetDateTime fromDate = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
        OffsetDateTime toDate   = OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
        if (dateRange != null) {
            OffsetDateTime now = OffsetDateTime.now();
            switch (dateRange.toLowerCase()) {
                case "today" -> {
                    fromDate = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
                    toDate = fromDate.plusDays(1).minusNanos(1);
                }
                case "week" -> {
                    fromDate = now.toLocalDate()
                            .with(java.time.DayOfWeek.MONDAY)
                            .atStartOfDay().atOffset(now.getOffset());
                    toDate = fromDate.plusWeeks(1).minusNanos(1);
                }
                case "month" -> {
                    fromDate = now.toLocalDate().withDayOfMonth(1).atStartOfDay().atOffset(now.getOffset());
                    toDate = fromDate.plusMonths(1).minusNanos(1);
                }
                default -> {}
            }
        }

        Page<PostCollectionEntity> collectionsPage;
        if ("review".equalsIgnoreCase(type)) {
            WorkspaceRole callerRole = WorkspaceContext.getRole();
            EnumSet<WorkspaceCapability> capabilities = workspaceCapabilityService.getEffectiveCapabilities(
                    workspaceId,
                    userId,
                    callerRole
            );
            if (!capabilities.contains(WorkspaceCapability.APPROVE_POSTS)
                    && !capabilities.contains(WorkspaceCapability.REQUEST_CHANGES)) {
                throw new SocialRavenException("Approval workflow access is required", HttpStatus.FORBIDDEN);
            }
            Sort reviewSort = "asc".equalsIgnoreCase(sortDir)
                    ? Sort.by("reviewSubmittedAt").ascending()
                    : Sort.by("reviewSubmittedAt").descending();
            Pageable reviewPageable = PageRequest.of(page, 12, reviewSort);
            if (hasAccountFilter && hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findReviewCollectionsWithAccountsAndPlatform(
                        workspaceId, searchPattern, platformStr, providerUserIds, reviewPageable);
            } else if (hasAccountFilter) {
                collectionsPage = postCollectionRepo.findReviewCollectionsWithAccounts(
                        workspaceId, searchPattern, providerUserIds, reviewPageable);
            } else if (hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findReviewCollectionsByPlatform(
                        workspaceId, searchPattern, platformStr, reviewPageable);
            } else {
                collectionsPage = postCollectionRepo.findReviewCollections(
                        workspaceId, searchPattern, reviewPageable);
            }
        } else if ("scheduled".equalsIgnoreCase(type)) {
            if (hasAccountFilter && hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findScheduledCollectionsWithAccountsAndPlatform(
                        workspaceId, searchPattern, platformStr, providerUserIds, fromDate, toDate, pageable);
            } else if (hasAccountFilter) {
                collectionsPage = postCollectionRepo.findScheduledCollectionsWithAccounts(
                        workspaceId, searchPattern, providerUserIds, fromDate, toDate, pageable);
            } else if (hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findScheduledCollectionsByPlatform(
                        workspaceId, searchPattern, platformStr, fromDate, toDate, pageable);
            } else {
                collectionsPage = postCollectionRepo.findScheduledCollections(
                        workspaceId, searchPattern, fromDate, toDate, pageable);
            }
        } else if ("published".equalsIgnoreCase(type)) {
            if (hasAccountFilter && hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findPublishedCollectionsWithAccountsAndPlatform(
                        workspaceId, searchPattern, platformStr, providerUserIds, fromDate, toDate, pageable);
            } else if (hasAccountFilter) {
                collectionsPage = postCollectionRepo.findPublishedCollectionsWithAccounts(
                        workspaceId, searchPattern, providerUserIds, fromDate, toDate, pageable);
            } else if (hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findPublishedCollectionsByPlatform(
                        workspaceId, searchPattern, platformStr, fromDate, toDate, pageable);
            } else {
                collectionsPage = postCollectionRepo.findPublishedCollections(
                        workspaceId, searchPattern, fromDate, toDate, pageable);
            }
        } else if ("draft".equalsIgnoreCase(type)) {
            Sort draftSort = "asc".equalsIgnoreCase(sortDir)
                    ? Sort.by("id").ascending()
                    : Sort.by("id").descending();
            Pageable draftPageable = PageRequest.of(page, 12, draftSort);
            if (hasPlatformFilter) {
                collectionsPage = postCollectionRepo.findDraftCollectionsByPlatform(
                        workspaceId, searchPattern, platformStr, draftPageable);
            } else {
                collectionsPage = postCollectionRepo.findDraftCollections(
                        workspaceId, searchPattern, draftPageable);
            }
        } else {
            collectionsPage = postCollectionRepo.findByWorkspaceIdOrderByScheduledTimeDesc(workspaceId, pageable);
        }
        WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot =
                workspaceApprovalRuleService.getSnapshot(workspaceId);
        return collectionsPage.map(c -> getPostCollectionResponse(c, connectedAccountMap, false, approvalRuleSnapshot));
    }

    @SuppressWarnings("unchecked")
    private PostCollectionResponse getPostCollectionResponse(
            PostCollectionEntity collection,
            Map<String, ConnectedAccount> connectedAccountMap,
            boolean includeReviewHistory,
            WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot) {

        List<PostEntity> posts = collection.getPosts();
        int publishedChannelCount = posts != null
                ? (int) posts.stream().filter(p -> p.getPostStatus() == PostStatus.PUBLISHED).count()
                : 0;
        int failedChannelCount = posts != null
                ? (int) posts.stream().filter(p -> p.getPostStatus() == PostStatus.FAILED).count()
                : 0;

        List<MediaResponse> mediaDtos = collection.getMediaFiles() != null
                ? collection.getMediaFiles().stream()
                        .map(m -> new MediaResponse(
                                m.getId(),
                                m.getFileName(),
                                m.getMimeType(),
                                m.getSize(),
                                storageService.generatePresignedGetUrl(m.getFileKey(), Duration.ofMinutes(10)),
                                m.getFileKey()
                        )).toList()
                : List.of();

        Map<Long, WorkspacePostAnalyticsEntity> analyticsByPostId = posts != null && !posts.isEmpty()
                ? workspacePostAnalyticsRepo.findAllByPostIdIn(
                                posts.stream().map(PostEntity::getId).toList()
                        ).stream()
                        .collect(Collectors.toMap(
                                WorkspacePostAnalyticsEntity::getPostId,
                                analytics -> analytics,
                                (left, right) -> right
                        ))
                : Map.of();

        List<PostResponse> postDtos = posts != null
                ? posts.stream().map(p -> getPostResponse(p, connectedAccountMap, analyticsByPostId.get(p.getId()))).toList()
                : List.of();

        List<PostCollectionReviewHistoryResponse> reviewHistory = includeReviewHistory
                ? postCollectionReviewHistoryRepo.findAllByPostCollectionIdOrderByCreatedAtAsc(collection.getId()).stream()
                .map(this::toReviewHistoryResponse)
                .toList()
                : null;
        List<PostCollectionVersionResponse> versionHistory = includeReviewHistory
                ? postCollectionVersionService.buildVersionHistory(collection.getId())
                : null;
        PostCollectionApprovalDiffResponse approvedDiff = includeReviewHistory
                ? postCollectionVersionService.buildApprovedDiff(collection)
                : null;
        List<PostCollectionActivityTimelineEntryResponse> activityTimeline = includeReviewHistory
                ? buildActivityTimeline(reviewHistory, versionHistory)
                : null;

        Map<String, Object> platformConfigsMap = null;
        if (collection.getPlatformConfigs() != null && !collection.getPlatformConfigs().isBlank()) {
            try {
                platformConfigsMap = objectMapper.readValue(collection.getPlatformConfigs(), Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse platformConfigs for collection {}: {}", collection.getId(), e.getMessage());
            }
        }

        PostReviewStatus reviewStatus = collection.getReviewStatus() != null
                ? collection.getReviewStatus()
                : PostReviewStatus.DRAFT;
        String overallStatus = collection.isDraft()
                ? reviewStatus.name()
                : deriveOverallStatus(posts);
        WorkspaceApprovalMode effectiveApprovalMode =
                workspaceApprovalRuleService.resolveApprovalMode(approvalRuleSnapshot, collection);

        return new PostCollectionResponse(
                collection.getId(),
                collection.getDescription(),
                collection.getScheduledTime(),
                collection.getPostCollectionType().name(),
                overallStatus,
                reviewStatus.name(),
                collection.getApprovalModeOverride() != null ? collection.getApprovalModeOverride().name() : null,
                effectiveApprovalMode.name(),
                collection.getReviewSubmittedAt(),
                collection.getApprovedAt(),
                collection.isApprovalLocked(),
                collection.getApprovalLockedAt(),
                collection.getRequiredApprovalSteps(),
                collection.getCompletedApprovalSteps(),
                collection.getNextApprovalStage() != null ? collection.getNextApprovalStage().name() : null,
                postDtos,
                mediaDtos,
                reviewHistory,
                collection.getApprovalReminderAttemptCount(),
                collection.getLastApprovalReminderSentAt(),
                collection.getNextApprovalReminderAt(),
                collection.getApprovalEscalatedAt(),
                versionHistory,
                approvedDiff,
                activityTimeline,
                platformConfigsMap,
                collection.getFailureState() != null ? collection.getFailureState().name() : RecoveryState.NONE.name(),
                collection.getFailureReasonSummary(),
                collection.getNotificationAttemptCount(),
                collection.getRecoveryCollectionId(),
                collection.getRecoverySourceCollectionId(),
                collection.getFailureState() == RecoveryState.RECOVERY_REQUIRED,
                collection.getRecoveryCollectionId() != null || collection.getFailureState() == RecoveryState.RECOVERED,
                publishedChannelCount,
                failedChannelCount
        );
    }

    private PostResponse getPostResponse(PostEntity post, Map<String, ConnectedAccount> connectedAccountMap) {
        return getPostResponse(
                post,
                connectedAccountMap,
                workspacePostAnalyticsRepo.findByPostId(post.getId()).orElse(null)
        );
    }

    private PostResponse getPostResponse(PostEntity post,
                                         Map<String, ConnectedAccount> connectedAccountMap,
                                         WorkspacePostAnalyticsEntity analytics) {
        PostCollectionEntity postCollection = post.getPostCollection();
        ConnectedAccount connectedAccount = connectedAccountMap.get(post.getProviderUserId());
        List<PostMediaEntity> mediaList = postCollection.getMediaFiles();

        List<MediaResponse> mediaDtos =
                mediaList.stream().map(m ->
                        new MediaResponse(
                                m.getId(),
                                m.getFileName(),
                                m.getMimeType(),
                                m.getSize(),
                                storageService.generatePresignedGetUrl(m.getFileKey(), Duration.ofMinutes(10)),
                                m.getFileKey()
                        )
                ).toList();

        return new PostResponse(
                post.getId(),
                postCollection.getId(),
                connectedAccount != null ? ProviderPlatformMapper.getProviderByPlatform(connectedAccount.getPlatform()) : null,
                postCollection.getDescription(),
                post.getPostStatus().toString(),
                post.getScheduledTime(),
                mediaDtos,
                connectedAccount,
                toAnalyticsSummary(analytics)
        );
    }

    private PostAnalyticsSummaryResponse toAnalyticsSummary(WorkspacePostAnalyticsEntity analytics) {
        if (analytics == null) {
            return null;
        }
        return new PostAnalyticsSummaryResponse(
                analytics.getFreshnessStatus() != null ? analytics.getFreshnessStatus().name() : null,
                analytics.getLastCollectedAt(),
                analytics.getImpressions(),
                analytics.getReach(),
                analytics.getLikes(),
                analytics.getComments(),
                analytics.getShares(),
                analytics.getSaves(),
                analytics.getClicks(),
                analytics.getVideoViews(),
                analytics.getWatchTimeMinutes(),
                analytics.getEngagements(),
                analytics.getEngagementRate()
        );
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getUserPosts(String userId, int page, PostStatus postStatus) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        Pageable pageable = page == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, 12, Sort.by("scheduledTime").descending());

        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(workspaceId, false);
        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));

        Page<PostEntity> postsPage = postRepo.findByPostCollectionWorkspaceIdAndPostStatus(workspaceId, postStatus, pageable);
        return postsPage.map(p -> getPostResponse(p, connectedAccountMap));
    }

    @Transactional(readOnly = true)
    public PostResponse getPostById(String userId, Long postId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostEntity post = postRepo.findPostWithCollectionAndMedia(postId);
        if (post == null) {
            throw new SocialRavenException("Post not found", HttpStatus.NOT_FOUND);
        }
        if (!workspaceId.equals(post.getPostCollection().getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        List<ConnectedAccount> connectedAccounts = accountProfileService.getAllConnectedAccounts(workspaceId, false);
        Map<String, ConnectedAccount> connectedAccountMap = connectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));
        return getPostResponse(post, connectedAccountMap);
    }

    @Transactional
    public void deletePostById(String userId, Long postId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostEntity post = postRepo.findById(postId)
                .orElseThrow(() -> new SocialRavenException("Post not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(post.getPostCollection().getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zrem(PostPoolHelper.getPostsPoolName(), postId.toString());
        }
        postRepo.deleteById(postId);
    }

    @Transactional(readOnly = true)
    public List<CalendarPostResponse> getCalendarPosts(
            String userId,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            List<String> providerUserIds) {
        String workspaceId = WorkspaceContext.getWorkspaceId();

        List<PostEntity> posts;
        if (CollectionUtils.isEmpty(providerUserIds)) {
            posts = postRepo.findCalendarPosts(workspaceId, startTime, endTime);
        } else {
            posts = postRepo.findCalendarPostsFiltered(workspaceId, startTime, endTime, providerUserIds);
        }

        return posts.stream()
                .map(p -> {
                    PostCollectionEntity pc = p.getPostCollection();
                    return new CalendarPostResponse(
                            p.getId(),
                            pc.getId(),
                            p.getProvider().name().toLowerCase(),
                            p.getProviderUserId(),
                            p.getPostStatus().name(),
                            pc.getPostCollectionType().name(),
                            p.getScheduledTime()
                    );
                })
                .toList();
    }

    private String deriveOverallStatus(List<PostEntity> posts) {
        if (posts == null || posts.isEmpty()) return "SCHEDULED";
        long total = posts.size();
        long scheduled = posts.stream().filter(p -> p.getPostStatus() == PostStatus.SCHEDULED).count();
        long posted = posts.stream().filter(p -> p.getPostStatus() == PostStatus.PUBLISHED).count();
        long failed = posts.stream().filter(p -> p.getPostStatus() == PostStatus.FAILED).count();

        if (scheduled == total) return "SCHEDULED";
        if (posted == total) return "PUBLISHED";
        if (failed == total) return "FAILED";
        return "PARTIAL_SUCCESS";
    }

    private PostCollectionEntity loadExistingRecoveryCollection(PostCollectionEntity failedCollection, String workspaceId) {
        if (failedCollection.getRecoveryCollectionId() == null) {
            return null;
        }
        return postCollectionRepo.findByIdAndWorkspaceId(failedCollection.getRecoveryCollectionId(), workspaceId)
                .orElse(null);
    }

    private void markFailedCollectionHandled(PostCollectionEntity failedCollection, String userId, Long recoveryCollectionId) {
        OffsetDateTime now = OffsetDateTime.now();
        failedCollection.setFailureState(RecoveryState.RECOVERED);
        failedCollection.setRecoveryCollectionId(recoveryCollectionId);
        failedCollection.setHandledAt(now);
        failedCollection.setHandledBy(userId);
        failedCollection.setNotificationStoppedAt(now);
        failedCollection.setNextNotificationAt(null);
        postCollectionRepo.save(failedCollection);
    }

    private PostCollectionEntity cloneAsRecoveryDraft(PostCollectionEntity failedCollection, String userId, String workspaceId) {
        PostCollectionEntity recoveryDraft = new PostCollectionEntity();
        recoveryDraft.setCreatedBy(userId);
        recoveryDraft.setWorkspaceId(workspaceId);
        recoveryDraft.setDescription(failedCollection.getDescription());
        recoveryDraft.setDraft(true);
        recoveryDraft.setReviewStatus(PostReviewStatus.DRAFT);
        recoveryDraft.setApprovalModeOverride(failedCollection.getApprovalModeOverride());
        recoveryDraft.setPostCollectionType(failedCollection.getPostCollectionType());
        recoveryDraft.setScheduledTime(null);
        recoveryDraft.setPlatformConfigs(failedCollection.getPlatformConfigs());
        recoveryDraft.setRecoverySourceCollectionId(failedCollection.getId());
        recoveryDraft.setFailureState(RecoveryState.NONE);

        List<PostMediaEntity> mediaCopies = failedCollection.getMediaFiles() != null
                ? failedCollection.getMediaFiles().stream()
                .map(media -> {
                    PostMediaEntity copy = new PostMediaEntity();
                    copy.setFileKey(media.getFileKey());
                    copy.setFileName(media.getFileName());
                    copy.setMimeType(media.getMimeType());
                    copy.setSize(media.getSize());
                    copy.setPostCollection(recoveryDraft);
                    return copy;
                })
                .toList()
                : List.of();
        recoveryDraft.setMediaFiles(new ArrayList<>(mediaCopies));

        List<PostEntity> postCopies = failedCollection.getPosts() != null
                ? failedCollection.getPosts().stream()
                .filter(post -> post.getPostStatus() == PostStatus.FAILED)
                .map(post -> {
                    PostEntity copy = new PostEntity();
                    copy.setProvider(post.getProvider());
                    copy.setProviderUserId(post.getProviderUserId());
                    copy.setPostStatus(PostStatus.DRAFT);
                    copy.setPostType(post.getPostType());
                    copy.setScheduledTime(null);
                    copy.setPostCollection(recoveryDraft);
                    return copy;
                })
                .toList()
                : List.of();
        recoveryDraft.setPosts(new ArrayList<>(postCopies));

        return recoveryDraft;
    }

    private PostCollectionResponse buildResponse(PostCollectionEntity collection, String workspaceId, boolean includeReviewHistory) {
        List<ConnectedAccount> allConnectedAccounts = accountProfileService.getAllConnectedAccounts(workspaceId);
        Map<String, ConnectedAccount> connectedAccountMap = allConnectedAccounts.stream()
                .collect(Collectors.toMap(ConnectedAccount::getProviderUserId, account -> account));
        return getPostCollectionResponse(
                collection,
                connectedAccountMap,
                includeReviewHistory,
                workspaceApprovalRuleService.getSnapshot(workspaceId)
        );
    }

    private PostCollectionReviewHistoryResponse toReviewHistoryResponse(PostCollectionReviewHistoryEntity historyEntity) {
        String actorDisplayName;
        if (historyEntity.getActorType() == PostActorType.CLIENT_REVIEWER) {
            actorDisplayName = resolveClientReviewerDisplayName(historyEntity.getActorDisplayName(), historyEntity.getActorEmail());
        } else if (historyEntity.getActorType() == PostActorType.SYSTEM) {
            actorDisplayName = historyEntity.getActorDisplayName() != null && !historyEntity.getActorDisplayName().isBlank()
                    ? historyEntity.getActorDisplayName().trim()
                    : "SocialRaven";
        } else if (historyEntity.getActorDisplayName() != null && !historyEntity.getActorDisplayName().isBlank()) {
            actorDisplayName = historyEntity.getActorDisplayName().trim();
        } else {
            actorDisplayName = resolveDisplayName(historyEntity.getActorUserId());
        }
        return new PostCollectionReviewHistoryResponse(
                historyEntity.getId(),
                historyEntity.getAction().name(),
                historyEntity.getFromStatus().name(),
                historyEntity.getToStatus().name(),
                historyEntity.getActorType() != null ? historyEntity.getActorType().name() : PostActorType.WORKSPACE_USER.name(),
                historyEntity.getActorUserId(),
                actorDisplayName,
                historyEntity.getNote(),
                historyEntity.getCreatedAt()
        );
    }

    private void transitionToInReview(PostCollectionEntity collection,
                                      String actorUserId,
                                      String note,
                                      int requiredApprovalSteps) {
        assertReadyForScheduling(collection);
        PostReviewStatus fromStatus = collection.getReviewStatus() != null
                ? collection.getReviewStatus()
                : PostReviewStatus.DRAFT;
        PostReviewAction action = fromStatus == PostReviewStatus.CHANGES_REQUESTED
                ? PostReviewAction.RESUBMITTED
                : PostReviewAction.SUBMITTED;
        OffsetDateTime now = OffsetDateTime.now();

        collection.setDraft(true);
        collection.setReviewStatus(PostReviewStatus.IN_REVIEW);
        collection.setReviewSubmittedAt(now);
        collection.setReviewSubmittedBy(actorUserId);
        collection.setApprovedAt(null);
        collection.setApprovedBy(null);
        collection.setApprovalLocked(false);
        collection.setApprovalLockedAt(null);
        collection.setRequiredApprovalSteps(requiredApprovalSteps);
        collection.setCompletedApprovalSteps(0);
        collection.setNextApprovalStage(requiredApprovalSteps > 0 ? PostApprovalStage.APPROVER : null);
        resetApprovalReminderState(collection, now);

        if (collection.getPosts() != null) {
            for (PostEntity post : collection.getPosts()) {
                post.setScheduledTime(collection.getScheduledTime());
                post.setPostStatus(PostStatus.DRAFT);
            }
        }

        recordReviewEvent(collection.getId(), action, fromStatus, PostReviewStatus.IN_REVIEW, actorUserId, note);
    }

    private void transitionToApprovedAndScheduled(PostCollectionEntity collection, String actorUserId, String note) {
        transitionToApprovedAndScheduled(
                collection,
                actorUserId,
                null,
                null,
                PostActorType.WORKSPACE_USER,
                note
        );
    }

    private void transitionToApprovedAwaitingSchedule(PostCollectionEntity collection, String actorUserId, String note) {
        transitionToApprovedAwaitingSchedule(
                collection,
                actorUserId,
                null,
                null,
                PostActorType.WORKSPACE_USER,
                note
        );
    }

    private void transitionToApprovedAwaitingSchedule(PostCollectionEntity collection,
                                                      String actorUserId,
                                                      String actorDisplayName,
                                                      String actorEmail,
                                                      PostActorType actorType,
                                                      String note) {
        assertReadyForScheduling(collection);
        PostReviewStatus fromStatus = collection.getReviewStatus() != null
                ? collection.getReviewStatus()
                : PostReviewStatus.DRAFT;
        OffsetDateTime now = OffsetDateTime.now();

        collection.setDraft(true);
        collection.setReviewStatus(PostReviewStatus.APPROVED);
        collection.setApprovedAt(now);
        collection.setApprovedBy(actorUserId);
        collection.setApprovalLocked(true);
        collection.setApprovalLockedAt(now);
        int requiredApprovalSteps = collection.getRequiredApprovalSteps() > 0
                ? collection.getRequiredApprovalSteps()
                : 1;
        collection.setRequiredApprovalSteps(requiredApprovalSteps);
        collection.setCompletedApprovalSteps(requiredApprovalSteps);
        collection.setNextApprovalStage(null);
        if (collection.getReviewSubmittedAt() == null) {
            collection.setReviewSubmittedAt(collection.getApprovedAt());
            collection.setReviewSubmittedBy(actorUserId);
        }
        clearApprovalReminderState(collection);

        if (collection.getPosts() != null) {
            for (PostEntity post : collection.getPosts()) {
                post.setScheduledTime(collection.getScheduledTime());
                post.setPostStatus(PostStatus.DRAFT);
            }
        }

        recordReviewEvent(
                collection.getId(),
                PostReviewAction.APPROVED,
                fromStatus,
                PostReviewStatus.APPROVED,
                actorUserId,
                actorDisplayName,
                actorEmail,
                actorType,
                note
        );
    }

    private void transitionToApprovedAndScheduled(PostCollectionEntity collection,
                                                  String actorUserId,
                                                  String actorDisplayName,
                                                  String actorEmail,
                                                  PostActorType actorType,
                                                  String note) {
        assertReadyForScheduling(collection);
        PostReviewStatus fromStatus = collection.getReviewStatus() != null
                ? collection.getReviewStatus()
                : PostReviewStatus.DRAFT;
        OffsetDateTime now = OffsetDateTime.now();

        collection.setDraft(false);
        collection.setReviewStatus(PostReviewStatus.APPROVED);
        collection.setApprovedAt(now);
        collection.setApprovedBy(actorUserId);
        collection.setApprovalLocked(true);
        collection.setApprovalLockedAt(now);
        int requiredApprovalSteps = collection.getRequiredApprovalSteps() > 0
                ? collection.getRequiredApprovalSteps()
                : 1;
        collection.setRequiredApprovalSteps(requiredApprovalSteps);
        collection.setCompletedApprovalSteps(requiredApprovalSteps);
        collection.setNextApprovalStage(null);
        if (collection.getReviewSubmittedAt() == null) {
            collection.setReviewSubmittedAt(collection.getApprovedAt());
            collection.setReviewSubmittedBy(actorUserId);
        }
        clearApprovalReminderState(collection);

        if (collection.getPosts() != null) {
            for (PostEntity post : collection.getPosts()) {
                post.setScheduledTime(collection.getScheduledTime());
                post.setPostStatus(PostStatus.SCHEDULED);
            }
        }

        recordReviewEvent(
                collection.getId(),
                PostReviewAction.APPROVED,
                fromStatus,
                PostReviewStatus.APPROVED,
                actorUserId,
                actorDisplayName,
                actorEmail,
                actorType,
                note
        );
    }

    private void transitionApprovedCollectionToScheduled(PostCollectionEntity collection) {
        assertReadyForScheduling(collection);
        collection.setDraft(false);
        collection.setReviewStatus(PostReviewStatus.APPROVED);
        collection.setApprovalLocked(true);
        if (collection.getApprovalLockedAt() == null) {
            collection.setApprovalLockedAt(OffsetDateTime.now());
        }

        if (collection.getPosts() != null) {
            for (PostEntity post : collection.getPosts()) {
                post.setScheduledTime(collection.getScheduledTime());
                post.setPostStatus(PostStatus.SCHEDULED);
            }
        }
    }

    private void transitionToOwnerFinalReview(PostCollectionEntity collection, String actorUserId, String note) {
        transitionToOwnerFinalReview(
                collection,
                actorUserId,
                null,
                null,
                PostActorType.WORKSPACE_USER,
                note
        );
    }

    private void transitionToOwnerFinalReview(PostCollectionEntity collection,
                                              String actorUserId,
                                              String actorDisplayName,
                                              String actorEmail,
                                              PostActorType actorType,
                                              String note) {
        assertReadyForScheduling(collection);
        if (collection.getRequiredApprovalSteps() < 2) {
            throw new SocialRavenException("This collection does not require multi-step approval", HttpStatus.BAD_REQUEST);
        }
        OffsetDateTime now = OffsetDateTime.now();

        collection.setDraft(true);
        collection.setReviewStatus(PostReviewStatus.IN_REVIEW);
        collection.setCompletedApprovalSteps(1);
        collection.setNextApprovalStage(PostApprovalStage.OWNER_FINAL);
        collection.setApprovedAt(null);
        collection.setApprovedBy(null);
        collection.setApprovalLocked(false);
        collection.setApprovalLockedAt(null);
        resetApprovalReminderState(collection, now);

        if (collection.getPosts() != null) {
            for (PostEntity post : collection.getPosts()) {
                post.setScheduledTime(collection.getScheduledTime());
                post.setPostStatus(PostStatus.DRAFT);
            }
        }

        recordReviewEvent(
                collection.getId(),
                PostReviewAction.STEP_APPROVED,
                PostReviewStatus.IN_REVIEW,
                PostReviewStatus.IN_REVIEW,
                actorUserId,
                actorDisplayName,
                actorEmail,
                actorType,
                note
        );
    }

    private void transitionToScheduledWithoutReview(PostCollectionEntity collection) {
        assertReadyForScheduling(collection);
        collection.setDraft(false);
        collection.setReviewStatus(PostReviewStatus.DRAFT);
        collection.setReviewSubmittedAt(null);
        collection.setReviewSubmittedBy(null);
        collection.setApprovedAt(null);
        collection.setApprovedBy(null);
        collection.setApprovalLocked(false);
        collection.setApprovalLockedAt(null);
        collection.setRequiredApprovalSteps(0);
        collection.setCompletedApprovalSteps(0);
        collection.setNextApprovalStage(null);
        clearApprovalReminderState(collection);

        if (collection.getPosts() != null) {
            for (PostEntity post : collection.getPosts()) {
                post.setScheduledTime(collection.getScheduledTime());
                post.setPostStatus(PostStatus.SCHEDULED);
            }
        }
    }

    private void transitionToChangesRequested(PostCollectionEntity collection, String actorUserId, String note) {
        transitionToChangesRequested(
                collection,
                actorUserId,
                null,
                null,
                PostActorType.WORKSPACE_USER,
                note
        );
    }

    private void transitionToChangesRequested(PostCollectionEntity collection,
                                              String actorUserId,
                                              String actorDisplayName,
                                              String actorEmail,
                                              PostActorType actorType,
                                              String note) {
        PostReviewStatus fromStatus = collection.getReviewStatus() != null
                ? collection.getReviewStatus()
                : PostReviewStatus.DRAFT;

        collection.setDraft(true);
        collection.setReviewStatus(PostReviewStatus.CHANGES_REQUESTED);
        collection.setApprovedAt(null);
        collection.setApprovedBy(null);
        collection.setApprovalLocked(false);
        collection.setApprovalLockedAt(null);
        collection.setCompletedApprovalSteps(0);
        collection.setNextApprovalStage(null);
        clearApprovalReminderState(collection);

        if (collection.getPosts() != null) {
            for (PostEntity post : collection.getPosts()) {
                post.setScheduledTime(collection.getScheduledTime());
                post.setPostStatus(PostStatus.DRAFT);
            }
        }

        recordReviewEvent(
                collection.getId(),
                PostReviewAction.CHANGES_REQUESTED,
                fromStatus,
                PostReviewStatus.CHANGES_REQUESTED,
                actorUserId,
                actorDisplayName,
                actorEmail,
                actorType,
                note
        );
    }

    private void transitionToReapprovalRequired(PostCollectionEntity collection,
                                                String actorUserId,
                                                String note,
                                                int requiredApprovalSteps) {
        assertReadyForScheduling(collection);
        OffsetDateTime now = OffsetDateTime.now();
        collection.setDraft(true);
        collection.setReviewStatus(PostReviewStatus.IN_REVIEW);
        collection.setReviewSubmittedAt(now);
        collection.setReviewSubmittedBy(actorUserId);
        collection.setApprovedAt(null);
        collection.setApprovedBy(null);
        collection.setApprovalLocked(false);
        collection.setApprovalLockedAt(null);
        collection.setRequiredApprovalSteps(requiredApprovalSteps);
        collection.setCompletedApprovalSteps(0);
        collection.setNextApprovalStage(requiredApprovalSteps > 0 ? PostApprovalStage.APPROVER : null);
        resetApprovalReminderState(collection, now);

        if (collection.getPosts() != null) {
            for (PostEntity post : collection.getPosts()) {
                post.setScheduledTime(collection.getScheduledTime());
                post.setPostStatus(PostStatus.DRAFT);
            }
        }

        recordReviewEvent(
                collection.getId(),
                PostReviewAction.REAPPROVAL_REQUIRED,
                PostReviewStatus.APPROVED,
                PostReviewStatus.IN_REVIEW,
                actorUserId,
                note
        );
    }

    private void recordReviewEvent(Long collectionId,
                                   PostReviewAction action,
                                   PostReviewStatus fromStatus,
                                   PostReviewStatus toStatus,
                                   String actorUserId,
                                   String note) {
        recordReviewEvent(
                collectionId,
                action,
                fromStatus,
                toStatus,
                actorUserId,
                null,
                null,
                PostActorType.WORKSPACE_USER,
                note
        );
    }

    private void recordReviewEvent(Long collectionId,
                                   PostReviewAction action,
                                   PostReviewStatus fromStatus,
                                   PostReviewStatus toStatus,
                                   String actorUserId,
                                   String actorDisplayName,
                                   String actorEmail,
                                   PostActorType actorType,
                                   String note) {
        if (collectionId == null) {
            return;
        }

        PostCollectionReviewHistoryEntity historyEntity = new PostCollectionReviewHistoryEntity();
        historyEntity.setPostCollectionId(collectionId);
        historyEntity.setAction(action);
        historyEntity.setFromStatus(fromStatus);
        historyEntity.setToStatus(toStatus);
        historyEntity.setActorUserId(actorUserId);
        historyEntity.setActorType(actorType != null ? actorType : PostActorType.WORKSPACE_USER);
        historyEntity.setActorDisplayName(actorDisplayName != null && !actorDisplayName.isBlank() ? actorDisplayName.trim() : null);
        historyEntity.setActorEmail(actorEmail != null && !actorEmail.isBlank() ? actorEmail.trim().toLowerCase() : null);
        historyEntity.setNote(note != null && !note.isBlank() ? note.trim() : null);
        historyEntity.setCreatedAt(OffsetDateTime.now());
        postCollectionReviewHistoryRepo.save(historyEntity);
    }

    private void assertReadyForScheduling(PostCollectionEntity collection) {
        if (collection.getScheduledTime() == null) {
            throw new SocialRavenException("scheduledTime is required before review or approval", HttpStatus.BAD_REQUEST);
        }
        if (CollectionUtils.isEmpty(collection.getPosts())) {
            throw new SocialRavenException(
                    "Select at least one connected account before review or approval",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private SubmissionDecision resolveSubmissionDecision(WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot,
                                                        PostCollectionEntity collection,
                                                        String workspaceId,
                                                        String userId,
                                                        WorkspaceRole callerRole) {
        WorkspaceApprovalMode approvalMode = workspaceApprovalRuleService.resolveApprovalMode(approvalRuleSnapshot, collection);
        EnumSet<WorkspaceCapability> capabilities = workspaceCapabilityService.getEffectiveCapabilities(
                workspaceId,
                userId,
                callerRole
        );

        return switch (approvalMode) {
            case NONE -> new SubmissionDecision(false, 0, approvalMode);
            case OPTIONAL -> capabilities.contains(WorkspaceCapability.PUBLISH_POSTS)
                    ? new SubmissionDecision(false, 0, approvalMode)
                    : new SubmissionDecision(true, 1, approvalMode);
            case REQUIRED -> new SubmissionDecision(true, 1, approvalMode);
            case MULTI_STEP -> new SubmissionDecision(true, 2, approvalMode);
        };
    }

    private void assertCanApprove(String workspaceId,
                                  String userId,
                                  WorkspaceRole callerRole,
                                  PostCollectionEntity collection) {
        PostApprovalStage nextApprovalStage = collection.getNextApprovalStage() != null
                ? collection.getNextApprovalStage()
                : PostApprovalStage.APPROVER;

        if (nextApprovalStage == PostApprovalStage.OWNER_FINAL) {
            if (callerRole != WorkspaceRole.OWNER) {
                throw new SocialRavenException("Only the workspace owner can give final approval", HttpStatus.FORBIDDEN);
            }
            return;
        }

        if (!workspaceCapabilityService.hasCapability(
                workspaceId,
                userId,
                callerRole,
                WorkspaceCapability.APPROVE_POSTS
        )) {
            throw new SocialRavenException("Approval capability is required", HttpStatus.FORBIDDEN);
        }
    }

    private void assertCanRequestChanges(String workspaceId, String userId, WorkspaceRole callerRole) {
        if (!workspaceCapabilityService.hasCapability(
                workspaceId,
                userId,
                callerRole,
                WorkspaceCapability.REQUEST_CHANGES
        )) {
            throw new SocialRavenException("Request changes capability is required", HttpStatus.FORBIDDEN);
        }
    }

    private void assertCanPublish(String workspaceId, String userId, WorkspaceRole callerRole) {
        if (!workspaceCapabilityService.hasCapability(
                workspaceId,
                userId,
                callerRole,
                WorkspaceCapability.PUBLISH_POSTS
        )) {
            throw new SocialRavenException("Publish capability is required", HttpStatus.FORBIDDEN);
        }
    }

    private void assertCanManageCampaignApprovalOverride(String workspaceId,
                                                         String userId,
                                                         WorkspaceRole callerRole,
                                                         boolean approvalOverrideRequested) {
        if (!approvalOverrideRequested) {
            return;
        }
        if (!workspaceCapabilityService.hasCapability(
                workspaceId,
                userId,
                callerRole,
                WorkspaceCapability.MANAGE_APPROVAL_RULES
        )) {
            throw new SocialRavenException(
                    "Approval overrides require approval-rule management access",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void enqueueScheduledPosts(List<PostEntity> posts) {
        if (CollectionUtils.isEmpty(posts)) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            for (PostEntity post : posts) {
                if (post.getId() == null || post.getScheduledTime() == null || post.getPostStatus() != PostStatus.SCHEDULED) {
                    continue;
                }
                long epochUtcMillis = post.getScheduledTime().toInstant().toEpochMilli();
                jedis.zadd(PostPoolHelper.getPostsPoolName(), epochUtcMillis, post.getId().toString());
                log.info("Scheduled Post Added to Redis pool: postId={}, scheduleUTC={}", post.getId(), post.getScheduledTime());
            }
        }
    }

    private void removePostIdsFromRedis(List<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zrem(PostPoolHelper.getPostsPoolName(), postIds.toArray(String[]::new));
        }
    }

    private List<String> collectScheduledRedisKeys(List<PostEntity> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return posts.stream()
                .filter(post -> post.getPostStatus() == PostStatus.SCHEDULED && post.getId() != null)
                .map(post -> post.getId().toString())
                .toList();
    }

    private void resetApprovalReminderState(PostCollectionEntity collection, OffsetDateTime referenceTime) {
        OffsetDateTime now = referenceTime != null ? referenceTime : OffsetDateTime.now();
        collection.setApprovalReminderAttemptCount(0);
        collection.setLastApprovalReminderSentAt(null);
        collection.setNextApprovalReminderAt(now.plusHours(initialApprovalReminderDelayHours));
        collection.setApprovalEscalatedAt(null);
    }

    private void clearApprovalReminderState(PostCollectionEntity collection) {
        collection.setApprovalReminderAttemptCount(0);
        collection.setLastApprovalReminderSentAt(null);
        collection.setNextApprovalReminderAt(null);
        collection.setApprovalEscalatedAt(null);
    }

    private int resolveReapprovalStepCount(WorkspaceApprovalRuleService.ApprovalRuleSnapshot approvalRuleSnapshot,
                                           PostCollectionEntity collection) {
        WorkspaceApprovalMode approvalMode = workspaceApprovalRuleService.resolveApprovalMode(approvalRuleSnapshot, collection);
        return approvalMode == WorkspaceApprovalMode.MULTI_STEP ? 2 : 1;
    }

    private MaterialState captureMaterialState(PostCollectionEntity collection) {
        String normalizedDescription = collection.getDescription() != null ? collection.getDescription() : "";
        String normalizedPlatformConfigs = normalizeJson(collection.getPlatformConfigs());
        List<String> mediaKeys = collection.getMediaFiles() == null
                ? List.of()
                : collection.getMediaFiles().stream()
                .map(PostMediaEntity::getFileKey)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        List<String> accountKeys = collection.getPosts() == null
                ? List.of()
                : collection.getPosts().stream()
                .filter(post -> post.getProvider() != null && post.getProviderUserId() != null)
                .map(post -> post.getProvider().name() + ":" + post.getProviderUserId())
                .sorted()
                .toList();
        return new MaterialState(
                normalizedDescription,
                collection.getScheduledTime(),
                normalizedPlatformConfigs,
                mediaKeys,
                accountKeys,
                collection.getApprovalModeOverride() != null ? collection.getApprovalModeOverride().name() : null
        );
    }

    private List<String> describeMaterialChanges(MaterialState before, MaterialState after) {
        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(before.description(), after.description())) {
            changedFields.add("caption");
        }
        if (!Objects.equals(before.scheduledTime(), after.scheduledTime())) {
            changedFields.add("schedule");
        }
        if (!Objects.equals(before.platformConfigs(), after.platformConfigs())) {
            changedFields.add("platform settings");
        }
        if (!Objects.equals(before.mediaKeys(), after.mediaKeys())) {
            changedFields.add("media");
        }
        if (!Objects.equals(before.accountKeys(), after.accountKeys())) {
            changedFields.add("target accounts");
        }
        if (!Objects.equals(before.approvalModeOverride(), after.approvalModeOverride())) {
            changedFields.add("campaign approval override");
        }
        return changedFields;
    }

    private String summarizeMaterialChanges(List<String> changedFields) {
        if (changedFields == null || changedFields.isEmpty()) {
            return "Material changes require reapproval.";
        }
        return "Material changes detected: " + String.join(", ", changedFields) + ". Reapproval is required.";
    }

    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (JsonProcessingException e) {
            return value;
        }
    }

    private String resolveDisplayName(String userId) {
        if (userId == null || userId.isBlank()) {
            return "Unknown user";
        }
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
            return profile.email().trim();
        }
        return userId;
    }

    private String resolveClientReviewerDisplayName(String actorDisplayName, String actorEmail) {
        if (actorDisplayName != null && !actorDisplayName.isBlank()) {
            return actorDisplayName.trim();
        }
        if (actorEmail != null && !actorEmail.isBlank()) {
            return actorEmail.trim();
        }
        return "Client reviewer";
    }

    private List<PostCollectionActivityTimelineEntryResponse> buildActivityTimeline(
            List<PostCollectionReviewHistoryResponse> reviewHistory,
            List<PostCollectionVersionResponse> versionHistory) {
        if ((reviewHistory == null || reviewHistory.isEmpty()) && (versionHistory == null || versionHistory.isEmpty())) {
            return List.of();
        }

        List<PostCollectionActivityTimelineEntryResponse> timeline = new ArrayList<>();
        Set<Long> linkedVersionIds = new HashSet<>();
        List<PostCollectionVersionResponse> versionEvents = versionHistory != null ? versionHistory : List.of();

        if (reviewHistory != null) {
            for (PostCollectionReviewHistoryResponse reviewEvent : reviewHistory) {
                PostCollectionVersionResponse matchedVersion =
                        findMatchingVersionEvent(reviewEvent, versionEvents, linkedVersionIds);
                if (matchedVersion != null) {
                    linkedVersionIds.add(matchedVersion.getId());
                }
                timeline.add(new PostCollectionActivityTimelineEntryResponse(
                        "review-" + reviewEvent.getId(),
                        "SYSTEM".equals(reviewEvent.getActorType()) ? "SYSTEM" : "WORKFLOW",
                        reviewEvent.getAction(),
                        formatReviewTimelineLabel(reviewEvent.getAction()),
                        reviewEvent.getActorType(),
                        reviewEvent.getActorUserId(),
                        reviewEvent.getActorDisplayName(),
                        reviewEvent.getCreatedAt(),
                        reviewEvent.getNote(),
                        reviewEvent.getFromStatus(),
                        reviewEvent.getToStatus(),
                        matchedVersion != null ? matchedVersion.getVersionNumber() : null,
                        matchedVersion != null ? matchedVersion.getScheduledTime() : null
                ));
            }
        }

        for (PostCollectionVersionResponse versionEvent : versionEvents) {
            PostCollectionVersionEvent resolvedEvent = PostCollectionVersionEvent.valueOf(versionEvent.getVersionEvent());
            if (linkedVersionIds.contains(versionEvent.getId()) || MIRRORED_WORKFLOW_VERSION_EVENTS.contains(resolvedEvent)) {
                continue;
            }
            timeline.add(new PostCollectionActivityTimelineEntryResponse(
                    "version-" + versionEvent.getId(),
                    "VERSION",
                    versionEvent.getVersionEvent(),
                    formatVersionTimelineLabel(versionEvent.getVersionEvent()),
                    versionEvent.getActorType(),
                    versionEvent.getActorUserId(),
                    versionEvent.getActorDisplayName(),
                    versionEvent.getCreatedAt(),
                    null,
                    null,
                    versionEvent.getReviewStatus(),
                    versionEvent.getVersionNumber(),
                    versionEvent.getScheduledTime()
            ));
        }

        timeline.sort((left, right) -> {
            int createdAtCompare = compareCreatedAt(right.getCreatedAt(), left.getCreatedAt());
            if (createdAtCompare != 0) {
                return createdAtCompare;
            }
            return String.valueOf(right.getEventKey()).compareTo(String.valueOf(left.getEventKey()));
        });
        return timeline;
    }

    private PostCollectionVersionResponse findMatchingVersionEvent(
            PostCollectionReviewHistoryResponse reviewEvent,
            List<PostCollectionVersionResponse> versionHistory,
            Set<Long> linkedVersionIds) {
        PostCollectionVersionEvent expectedVersionEvent;
        try {
            expectedVersionEvent = PostCollectionVersionEvent.valueOf(reviewEvent.getAction());
        } catch (IllegalArgumentException e) {
            return null;
        }

        for (PostCollectionVersionResponse versionEvent : versionHistory) {
            if (linkedVersionIds.contains(versionEvent.getId())
                    || PostCollectionVersionEvent.valueOf(versionEvent.getVersionEvent()) != expectedVersionEvent) {
                continue;
            }
            if (!Objects.equals(reviewEvent.getActorType(), versionEvent.getActorType())) {
                continue;
            }
            if (!sameActor(reviewEvent.getActorUserId(), reviewEvent.getActorDisplayName(), versionEvent)) {
                continue;
            }
            if (reviewEvent.getCreatedAt() == null || versionEvent.getCreatedAt() == null) {
                continue;
            }
            long differenceMillis = Math.abs(reviewEvent.getCreatedAt().toInstant().toEpochMilli()
                    - versionEvent.getCreatedAt().toInstant().toEpochMilli());
            if (differenceMillis <= TIMELINE_MATCH_WINDOW_MILLIS) {
                return versionEvent;
            }
        }
        return null;
    }

    private boolean sameActor(String actorUserId,
                              String actorDisplayName,
                              PostCollectionVersionResponse versionEvent) {
        if (actorUserId != null || versionEvent.getActorUserId() != null) {
            return Objects.equals(actorUserId, versionEvent.getActorUserId());
        }
        return normalizeActorDisplayName(actorDisplayName)
                .equals(normalizeActorDisplayName(versionEvent.getActorDisplayName()));
    }

    private String normalizeActorDisplayName(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private int compareCreatedAt(OffsetDateTime left, OffsetDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private String formatReviewTimelineLabel(String action) {
        return switch (PostReviewAction.valueOf(action)) {
            case SUBMITTED -> "Submitted for review";
            case RESUBMITTED -> "Resubmitted for review";
            case STEP_APPROVED -> "Completed approval step";
            case APPROVED -> "Approved";
            case CHANGES_REQUESTED -> "Changes requested";
            case REAPPROVAL_REQUIRED -> "Reapproval required";
            case REMINDER_SENT -> "Reminder sent";
            case ESCALATED -> "Approval escalated";
        };
    }

    private String formatVersionTimelineLabel(String versionEvent) {
        return switch (PostCollectionVersionEvent.valueOf(versionEvent)) {
            case CREATED -> "Created";
            case UPDATED -> "Saved material changes";
            case SUBMITTED -> "Submitted for review";
            case RESUBMITTED -> "Resubmitted for review";
            case STEP_APPROVED -> "Completed approval step";
            case APPROVED -> "Approved";
            case CHANGES_REQUESTED -> "Changes requested";
            case REAPPROVAL_REQUIRED -> "Reapproval required";
            case SCHEDULED_DIRECT -> "Scheduled without review";
            case SCHEDULED_AFTER_APPROVAL -> "Scheduled after approval";
            case RECOVERY_CREATED -> "Created recovery draft";
        };
    }

    private String formatAuditTimestamp(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return AUDIT_EXPORT_TIMESTAMP_FORMATTER.format(value.withOffsetSameInstant(ZoneOffset.UTC));
    }

    private String csvCell(String value) {
        String normalized = value != null ? sanitizeCsvValue(value) : "";
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
    }

    private String sanitizeCsvValue(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + value;
        }
        return value;
    }

    private boolean isAllFailed(List<PostEntity> posts) {
        return posts != null
                && !posts.isEmpty()
                && posts.stream().allMatch(post -> post.getPostStatus() == PostStatus.FAILED);
    }

    private boolean hasRecoverableFailedPosts(List<PostEntity> posts) {
        return posts != null
                && posts.stream().anyMatch(post -> post.getPostStatus() == PostStatus.FAILED);
    }

    private record SubmissionDecision(boolean requiresReview,
                                      int requiredApprovalSteps,
                                      WorkspaceApprovalMode approvalMode) {
    }

    private record MaterialState(String description,
                                 OffsetDateTime scheduledTime,
                                 String platformConfigs,
                                 List<String> mediaKeys,
                                 List<String> accountKeys,
                                 String approvalModeOverride) {
    }
}
