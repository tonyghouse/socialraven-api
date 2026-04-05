package com.tonyghouse.socialraven.service.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.constant.PostApprovalStage;
import com.tonyghouse.socialraven.constant.PostReviewLinkShareScope;
import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.constant.PostStatus;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.dto.CreatePostCollectionReviewLinkRequest;
import com.tonyghouse.socialraven.dto.MediaResponse;
import com.tonyghouse.socialraven.dto.PostCollaborationThreadResponse;
import com.tonyghouse.socialraven.dto.PostCollectionReviewLinkResponse;
import com.tonyghouse.socialraven.dto.PublicPostCollectionReviewResponse;
import com.tonyghouse.socialraven.dto.PublicReviewChannelResponse;
import com.tonyghouse.socialraven.dto.PublicReviewCommentRequest;
import com.tonyghouse.socialraven.dto.PublicReviewDecisionRequest;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostCollectionReviewLinkEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.PostMediaEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.PostCollectionReviewLinkRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import com.tonyghouse.socialraven.service.storage.StorageService;
import com.tonyghouse.socialraven.service.workspace.WorkspaceCapabilityService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostCollectionReviewLinkService {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {};
    private static final Pattern REVIEWER_EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSCODE_LENGTH = 6;
    private static final int MAX_PASSCODE_LENGTH = 64;

    @Autowired
    private PostCollectionRepo postCollectionRepo;

    @Autowired
    private PostCollectionReviewLinkRepo postCollectionReviewLinkRepo;

    @Autowired
    private WorkspaceCapabilityService workspaceCapabilityService;

    @Autowired
    private PostReviewLinkTokenService postReviewLinkTokenService;

    @Autowired
    private ClerkUserService clerkUserService;

    @Autowired
    private PostCollaborationService postCollaborationService;

    @Autowired
    private PostService postService;

    @Autowired
    private AccountProfileService accountProfileService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private ObjectMapper objectMapper;

    private final PasswordEncoder reviewLinkPasscodeEncoder = new BCryptPasswordEncoder();

    @Transactional(readOnly = true)
    public List<PostCollectionReviewLinkResponse> getReviewLinks(String userId, Long collectionId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole role = WorkspaceContext.getRole();
        PostCollectionEntity collection = requireCollection(collectionId, workspaceId);
        assertCanShareReviewLinks(workspaceId, userId, role);

        return postCollectionReviewLinkRepo.findAllByPostCollectionIdOrderByCreatedAtDesc(collection.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PostCollectionReviewLinkResponse createReviewLink(String userId,
                                                             Long collectionId,
                                                             CreatePostCollectionReviewLinkRequest request) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole role = WorkspaceContext.getRole();
        PostCollectionEntity collection = requireCollection(collectionId, workspaceId);
        assertCanShareReviewLinks(workspaceId, userId, role);
        if (!collection.isDraft()) {
            throw new SocialRavenException(
                    "Review links can only be created while the collection is still in workflow",
                    HttpStatus.CONFLICT
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = request != null && request.getExpiresAt() != null
                ? request.getExpiresAt()
                : now.plusDays(7);
        if (!expiresAt.isAfter(now)) {
            throw new SocialRavenException("expiresAt must be in the future", HttpStatus.BAD_REQUEST);
        }
        ShareTarget shareTarget = resolveShareTarget(collection, request);
        String passcode = normalizeOptionalPasscode(request != null ? request.getPasscode() : null);

        PostCollectionReviewLinkEntity link = new PostCollectionReviewLinkEntity();
        link.setId(UUID.randomUUID().toString());
        link.setPostCollectionId(collection.getId());
        link.setWorkspaceId(workspaceId);
        link.setCreatedByUserId(userId);
        link.setShareScope(shareTarget.scope());
        link.setSharedPostIds(writeSharedPostIds(shareTarget.sharedPostIds()));
        link.setPasscodeHash(passcode != null ? reviewLinkPasscodeEncoder.encode(passcode) : null);
        link.setExpiresAt(expiresAt);
        link.setCreatedAt(now);

        PostCollectionReviewLinkEntity saved = postCollectionReviewLinkRepo.save(link);
        return toResponse(saved);
    }

    @Transactional
    public void revokeReviewLink(String userId, Long collectionId, String reviewLinkId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        WorkspaceRole role = WorkspaceContext.getRole();
        PostCollectionEntity collection = requireCollection(collectionId, workspaceId);
        assertCanShareReviewLinks(workspaceId, userId, role);

        PostCollectionReviewLinkEntity link = postCollectionReviewLinkRepo.findByIdAndPostCollectionId(reviewLinkId, collection.getId())
                .orElseThrow(() -> new SocialRavenException("Review link not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(link.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (link.getRevokedAt() != null) {
            return;
        }

        link.setRevokedAt(OffsetDateTime.now());
        link.setRevokedByUserId(userId);
        postCollectionReviewLinkRepo.save(link);
    }

    @Transactional
    public PublicPostCollectionReviewResponse getPublicReview(String token, String passcode) {
        ResolvedReviewLink resolved = resolveReviewLink(token, passcode);
        markLastAccessed(resolved.link());
        return buildPublicResponse(resolved);
    }

    @Transactional
    public PostCollaborationThreadResponse addClientComment(String token,
                                                            String passcode,
                                                            PublicReviewCommentRequest request) {
        ResolvedReviewLink resolved = resolveReviewLink(token, passcode);
        assertLinkActive(resolved);
        if (!resolved.collection().isDraft()) {
            throw new SocialRavenException(
                    "This review link is read-only because the content has already been finalized",
                    HttpStatus.CONFLICT
            );
        }

        ReviewerIdentity identity = requireReviewerIdentity(
                request != null ? request.getReviewerName() : null,
                request != null ? request.getReviewerEmail() : null
        );
        markLastAccessed(resolved.link());
        return postCollaborationService.createClientVisibleComment(
                resolved.collection().getId(),
                resolved.collection().getWorkspaceId(),
                identity.displayName(),
                identity.email(),
                request != null ? request.getBody() : null,
                request != null ? request.getAnchorStart() : null,
                request != null ? request.getAnchorEnd() : null,
                request != null ? request.getAnchorText() : null,
                request != null ? request.getMediaId() : null,
                request != null ? request.getMediaMarkerX() : null,
                request != null ? request.getMediaMarkerY() : null
        );
    }

    @Transactional
    public PublicPostCollectionReviewResponse approveFromClient(String token,
                                                                String passcode,
                                                                PublicReviewDecisionRequest request) {
        ResolvedReviewLink resolved = resolveReviewLink(token, passcode);
        assertClientApprovalAllowed(resolved);
        ReviewerIdentity identity = requireReviewerIdentity(
                request != null ? request.getReviewerName() : null,
                request != null ? request.getReviewerEmail() : null
        );
        markLastAccessed(resolved.link());
        PostCollectionEntity updated = postService.approvePostCollectionFromClientReviewer(
                resolved.collection(),
                identity.displayName(),
                identity.email(),
                request != null ? request.getNote() : null
        );
        return buildPublicResponse(new ResolvedReviewLink(resolved.link(), updated, resolved.tokenExpiresAt()));
    }

    @Transactional
    public PublicPostCollectionReviewResponse rejectFromClient(String token,
                                                               String passcode,
                                                               PublicReviewDecisionRequest request) {
        ResolvedReviewLink resolved = resolveReviewLink(token, passcode);
        assertClientRejectionAllowed(resolved);
        ReviewerIdentity identity = requireReviewerIdentity(
                request != null ? request.getReviewerName() : null,
                request != null ? request.getReviewerEmail() : null
        );
        markLastAccessed(resolved.link());
        PostCollectionEntity updated = postService.requestChangesFromClientReviewer(
                resolved.collection(),
                identity.displayName(),
                identity.email(),
                request != null ? request.getNote() : null
        );
        return buildPublicResponse(new ResolvedReviewLink(resolved.link(), updated, resolved.tokenExpiresAt()));
    }

    private PostCollectionEntity requireCollection(Long collectionId, String workspaceId) {
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException("Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        return collection;
    }

    private void assertCanShareReviewLinks(String workspaceId, String userId, WorkspaceRole role) {
        if (!workspaceCapabilityService.hasCapability(
                workspaceId,
                userId,
                role,
                WorkspaceCapability.SHARE_REVIEW_LINKS
        )) {
            throw new SocialRavenException("Share review links capability is required", HttpStatus.FORBIDDEN);
        }
    }

    private PostCollectionReviewLinkResponse toResponse(PostCollectionReviewLinkEntity entity) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean active = entity.getRevokedAt() == null && entity.getExpiresAt() != null && entity.getExpiresAt().isAfter(now);
        return new PostCollectionReviewLinkResponse(
                entity.getId(),
                postReviewLinkTokenService.generateToken(entity.getId(), entity.getExpiresAt()),
                entity.getCreatedByUserId(),
                resolveDisplayName(entity.getCreatedByUserId()),
                entity.getShareScope() != null ? entity.getShareScope().name() : PostReviewLinkShareScope.CAMPAIGN.name(),
                readSharedPostIds(entity.getSharedPostIds()),
                entity.getPasscodeHash() != null && !entity.getPasscodeHash().isBlank(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt(),
                active
        );
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
            return profile.email().trim();
        }
        return userId;
    }

    private ResolvedReviewLink resolveReviewLink(String token, String passcode) {
        PostReviewLinkTokenService.ValidatedReviewLinkToken validatedToken = postReviewLinkTokenService.parseAndValidate(token);
        PostCollectionReviewLinkEntity link = postCollectionReviewLinkRepo.findById(validatedToken.reviewLinkId())
                .orElseThrow(() -> new SocialRavenException("Review link not found", HttpStatus.NOT_FOUND));
        if (link.getExpiresAt() == null
                || link.getExpiresAt().toEpochSecond() != validatedToken.expiresAt().toEpochSecond()) {
            throw new SocialRavenException("Review link is invalid", HttpStatus.NOT_FOUND);
        }

        PostCollectionEntity collection = postCollectionRepo.findById(link.getPostCollectionId())
                .orElseThrow(() -> new SocialRavenException("Post collection not found", HttpStatus.NOT_FOUND));
        if (!link.getWorkspaceId().equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Review link is invalid", HttpStatus.NOT_FOUND);
        }
        assertPasscodeAuthorized(link, passcode);

        return new ResolvedReviewLink(link, collection, validatedToken.expiresAt());
    }

    private void assertLinkActive(ResolvedReviewLink resolved) {
        if (resolved.link().getRevokedAt() != null) {
            throw new SocialRavenException("This review link has been revoked", HttpStatus.GONE);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (resolved.link().getExpiresAt() == null || !resolved.link().getExpiresAt().isAfter(now)) {
            throw new SocialRavenException("This review link has expired", HttpStatus.GONE);
        }
    }

    private void assertClientApprovalAllowed(ResolvedReviewLink resolved) {
        assertLinkActive(resolved);
        if (resolved.link().getShareScope() == PostReviewLinkShareScope.SELECTED_POSTS) {
            throw new SocialRavenException(
                    "Selected-post review links are feedback-only. Use a full campaign link for approval.",
                    HttpStatus.CONFLICT
            );
        }
        PostCollectionEntity collection = resolved.collection();
        PostReviewStatus reviewStatus = collection.getReviewStatus() != null
                ? collection.getReviewStatus()
                : PostReviewStatus.DRAFT;
        if (!collection.isDraft() || reviewStatus != PostReviewStatus.IN_REVIEW) {
            throw new SocialRavenException("This content is not currently awaiting client approval", HttpStatus.CONFLICT);
        }
        if (collection.getNextApprovalStage() == PostApprovalStage.OWNER_FINAL) {
            throw new SocialRavenException(
                    "This content already passed the client-review stage and is awaiting final internal sign-off",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void assertClientRejectionAllowed(ResolvedReviewLink resolved) {
        assertLinkActive(resolved);
        if (resolved.link().getShareScope() == PostReviewLinkShareScope.SELECTED_POSTS) {
            throw new SocialRavenException(
                    "Selected-post review links are feedback-only. Use a full campaign link for approval decisions.",
                    HttpStatus.CONFLICT
            );
        }
        PostCollectionEntity collection = resolved.collection();
        PostReviewStatus reviewStatus = collection.getReviewStatus() != null
                ? collection.getReviewStatus()
                : PostReviewStatus.DRAFT;
        if (!collection.isDraft() || reviewStatus != PostReviewStatus.IN_REVIEW) {
            throw new SocialRavenException("This content is not currently awaiting client review", HttpStatus.CONFLICT);
        }
    }

    private void markLastAccessed(PostCollectionReviewLinkEntity link) {
        link.setLastAccessedAt(OffsetDateTime.now());
        postCollectionReviewLinkRepo.save(link);
    }

    @SuppressWarnings("unchecked")
    private PublicPostCollectionReviewResponse buildPublicResponse(ResolvedReviewLink resolved) {
        PostCollectionEntity collection = resolved.collection();
        Map<String, ConnectedAccount> accountsByProviderUserId = accountProfileService.getAllConnectedAccounts(
                collection.getWorkspaceId(),
                false
        ).stream().collect(Collectors.toMap(
                ConnectedAccount::getProviderUserId,
                account -> account,
                (left, right) -> left,
                LinkedHashMap::new
        ));
        List<PostEntity> sharedPosts = resolveSharedPosts(collection, resolved.link());
        Set<String> sharedPlatforms = sharedPosts.stream()
                .map(post -> post.getProvider() != null ? post.getProvider().name() : null)
                .filter(platform -> platform != null && !platform.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> sharedPlatformKeys = sharedPlatforms.stream()
                .flatMap(platform -> java.util.stream.Stream.of(platform, platform.toLowerCase()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<PublicReviewChannelResponse> channels = sharedPosts.stream()
                .map(post -> {
                    ConnectedAccount account = accountsByProviderUserId.get(post.getProviderUserId());
                    return new PublicReviewChannelResponse(
                            post.getProvider() != null ? post.getProvider().name() : null,
                            account != null ? account.getUsername() : null,
                            account != null ? account.getProfilePicLink() : null
                    );
                })
                .toList();

        List<MediaResponse> media = collection.getMediaFiles() != null
                ? collection.getMediaFiles().stream()
                .map(this::toMediaResponse)
                .toList()
                : List.of();

        Map<String, Object> platformConfigs = null;
        if (collection.getPlatformConfigs() != null && !collection.getPlatformConfigs().isBlank()) {
            try {
                Map<String, Object> allPlatformConfigs = objectMapper.readValue(collection.getPlatformConfigs(), Map.class);
                if (allPlatformConfigs == null) {
                    platformConfigs = null;
                } else if (resolved.link().getShareScope() == PostReviewLinkShareScope.SELECTED_POSTS) {
                    platformConfigs = allPlatformConfigs.entrySet().stream()
                            .filter(entry -> sharedPlatformKeys.contains(entry.getKey()))
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (left, right) -> left,
                                    LinkedHashMap::new
                            ));
                } else {
                    platformConfigs = allPlatformConfigs;
                }
            } catch (Exception ignored) {
                platformConfigs = null;
            }
        }

        boolean linkRevoked = resolved.link().getRevokedAt() != null;
        boolean linkExpired = resolved.link().getExpiresAt() == null || !resolved.link().getExpiresAt().isAfter(OffsetDateTime.now());
        boolean activeLink = !linkRevoked && !linkExpired;
        PostReviewStatus reviewStatus = collection.getReviewStatus() != null
                ? collection.getReviewStatus()
                : PostReviewStatus.DRAFT;
        boolean campaignScope = resolved.link().getShareScope() != PostReviewLinkShareScope.SELECTED_POSTS;
        boolean canComment = activeLink && collection.isDraft() && reviewStatus != PostReviewStatus.APPROVED;
        boolean canApprove = activeLink
                && campaignScope
                && collection.isDraft()
                && reviewStatus == PostReviewStatus.IN_REVIEW
                && collection.getNextApprovalStage() != PostApprovalStage.OWNER_FINAL;
        boolean canReject = activeLink
                && campaignScope
                && collection.isDraft()
                && reviewStatus == PostReviewStatus.IN_REVIEW;

        List<PostCollaborationThreadResponse> collaborationThreads = postCollaborationService.getClientVisibleThreads(
                collection.getId(),
                collection.getWorkspaceId()
        );

        return new PublicPostCollectionReviewResponse(
                collection.getId(),
                collection.getDescription(),
                collection.getScheduledTime(),
                collection.getPostCollectionType() != null ? collection.getPostCollectionType().name() : null,
                deriveOverallStatus(collection),
                reviewStatus.name(),
                collection.getNextApprovalStage() != null ? collection.getNextApprovalStage().name() : null,
                resolved.link().getShareScope() != null
                        ? resolved.link().getShareScope().name()
                        : PostReviewLinkShareScope.CAMPAIGN.name(),
                channels,
                media,
                platformConfigs,
                collaborationThreads,
                resolved.link().getExpiresAt(),
                linkExpired,
                linkRevoked,
                canComment,
                canApprove,
                canReject
        );
    }

    private MediaResponse toMediaResponse(PostMediaEntity media) {
        return new MediaResponse(
                media.getId(),
                media.getFileName(),
                media.getMimeType(),
                media.getSize(),
                storageService.generatePresignedGetUrl(media.getFileKey(), Duration.ofMinutes(10)),
                media.getFileKey()
        );
    }

    private String deriveOverallStatus(PostCollectionEntity collection) {
        if (collection.isDraft()) {
            PostReviewStatus reviewStatus = collection.getReviewStatus() != null
                    ? collection.getReviewStatus()
                    : PostReviewStatus.DRAFT;
            return reviewStatus.name();
        }

        List<PostEntity> posts = collection.getPosts();
        if (posts == null || posts.isEmpty()) {
            return "SCHEDULED";
        }

        long total = posts.size();
        long scheduled = posts.stream().filter(post -> post.getPostStatus() == PostStatus.SCHEDULED).count();
        long published = posts.stream().filter(post -> post.getPostStatus() == PostStatus.PUBLISHED).count();
        long failed = posts.stream().filter(post -> post.getPostStatus() == PostStatus.FAILED).count();

        if (scheduled == total) {
            return "SCHEDULED";
        }
        if (published == total) {
            return "PUBLISHED";
        }
        if (failed == total) {
            return "FAILED";
        }
        return "PARTIAL_SUCCESS";
    }

    private ReviewerIdentity requireReviewerIdentity(String reviewerName, String reviewerEmail) {
        String displayName = normalizeRequiredValue(reviewerName, "reviewerName is required");
        String email = normalizeRequiredValue(reviewerEmail, "reviewerEmail is required").toLowerCase();
        if (!REVIEWER_EMAIL_PATTERN.matcher(email).matches()) {
            throw new SocialRavenException("reviewerEmail must be a valid email address", HttpStatus.BAD_REQUEST);
        }
        return new ReviewerIdentity(displayName, email);
    }

    private String normalizeRequiredValue(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new SocialRavenException(message, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private ShareTarget resolveShareTarget(PostCollectionEntity collection,
                                           CreatePostCollectionReviewLinkRequest request) {
        PostReviewLinkShareScope requestedScope = request != null && request.getShareScope() != null
                ? request.getShareScope()
                : PostReviewLinkShareScope.CAMPAIGN;
        if (requestedScope == PostReviewLinkShareScope.CAMPAIGN) {
            return new ShareTarget(PostReviewLinkShareScope.CAMPAIGN, List.of());
        }

        if (collection.getPosts() == null || collection.getPosts().isEmpty()) {
            throw new SocialRavenException(
                    "Selected-post sharing requires at least one campaign post",
                    HttpStatus.BAD_REQUEST
            );
        }

        List<Long> requestedPostIds = request != null ? request.getSharedPostIds() : null;
        if (requestedPostIds == null || requestedPostIds.isEmpty()) {
            throw new SocialRavenException(
                    "sharedPostIds is required when shareScope is SELECTED_POSTS",
                    HttpStatus.BAD_REQUEST
            );
        }

        Set<Long> collectionPostIds = collection.getPosts().stream()
                .map(PostEntity::getId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> normalized = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Long postId : requestedPostIds) {
            if (postId == null || !seen.add(postId)) {
                continue;
            }
            if (!collectionPostIds.contains(postId)) {
                throw new SocialRavenException(
                        "All sharedPostIds must belong to this campaign",
                        HttpStatus.BAD_REQUEST
                );
            }
            normalized.add(postId);
        }
        if (normalized.isEmpty()) {
            throw new SocialRavenException(
                    "sharedPostIds is required when shareScope is SELECTED_POSTS",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (normalized.size() == collectionPostIds.size() && collectionPostIds.containsAll(normalized)) {
            return new ShareTarget(PostReviewLinkShareScope.CAMPAIGN, List.of());
        }
        return new ShareTarget(PostReviewLinkShareScope.SELECTED_POSTS, normalized);
    }

    private List<PostEntity> resolveSharedPosts(PostCollectionEntity collection, PostCollectionReviewLinkEntity link) {
        List<PostEntity> posts = collection.getPosts() != null ? collection.getPosts() : List.of();
        if (link.getShareScope() != PostReviewLinkShareScope.SELECTED_POSTS) {
            return posts;
        }

        Set<Long> sharedPostIds = new LinkedHashSet<>(readSharedPostIds(link.getSharedPostIds()));
        if (sharedPostIds.isEmpty()) {
            return posts;
        }

        return posts.stream()
                .filter(post -> post.getId() != null && sharedPostIds.contains(post.getId()))
                .toList();
    }

    private List<Long> readSharedPostIds(String sharedPostIdsJson) {
        if (sharedPostIdsJson == null || sharedPostIdsJson.isBlank()) {
            return List.of();
        }
        try {
            List<Long> sharedPostIds = objectMapper.readValue(sharedPostIdsJson, LONG_LIST_TYPE);
            return sharedPostIds != null ? sharedPostIds : List.of();
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private String writeSharedPostIds(List<Long> sharedPostIds) {
        try {
            return objectMapper.writeValueAsString(sharedPostIds != null ? sharedPostIds : List.of());
        } catch (JsonProcessingException ex) {
            throw new SocialRavenException("Failed to store shared post ids", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String normalizeOptionalPasscode(String passcode) {
        if (passcode == null) {
            return null;
        }
        String normalized = passcode.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() < MIN_PASSCODE_LENGTH || normalized.length() > MAX_PASSCODE_LENGTH) {
            throw new SocialRavenException(
                    "Review link passcodes must be between 6 and 64 characters",
                    HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    private void assertPasscodeAuthorized(PostCollectionReviewLinkEntity link, String providedPasscode) {
        if (link.getPasscodeHash() == null || link.getPasscodeHash().isBlank()) {
            return;
        }

        String normalizedPasscode = normalizeOptionalPasscode(providedPasscode);
        if (normalizedPasscode == null) {
            throw new SocialRavenException("This review link requires a passcode", HttpStatus.FORBIDDEN);
        }
        if (!reviewLinkPasscodeEncoder.matches(normalizedPasscode, link.getPasscodeHash())) {
            throw new SocialRavenException("Incorrect review link passcode", HttpStatus.FORBIDDEN);
        }
    }

    private record ReviewerIdentity(String displayName, String email) {
    }

    private record ShareTarget(PostReviewLinkShareScope scope, List<Long> sharedPostIds) {
    }

    private record ResolvedReviewLink(PostCollectionReviewLinkEntity link,
                                      PostCollectionEntity collection,
                                      OffsetDateTime tokenExpiresAt) {
    }
}
