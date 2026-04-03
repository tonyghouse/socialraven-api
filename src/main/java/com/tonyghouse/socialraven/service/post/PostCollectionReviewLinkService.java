package com.tonyghouse.socialraven.service.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.constant.PostApprovalStage;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostCollectionReviewLinkService {

    private static final Pattern REVIEWER_EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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

        PostCollectionReviewLinkEntity link = new PostCollectionReviewLinkEntity();
        link.setId(UUID.randomUUID().toString());
        link.setPostCollectionId(collection.getId());
        link.setWorkspaceId(workspaceId);
        link.setCreatedByUserId(userId);
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

    @Transactional(readOnly = true)
    public PublicPostCollectionReviewResponse getPublicReview(String token) {
        ResolvedReviewLink resolved = resolveReviewLink(token);
        return buildPublicResponse(resolved);
    }

    @Transactional
    public PostCollaborationThreadResponse addClientComment(String token, PublicReviewCommentRequest request) {
        ResolvedReviewLink resolved = resolveReviewLink(token);
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
                request != null ? request.getBody() : null
        );
    }

    @Transactional
    public PublicPostCollectionReviewResponse approveFromClient(String token, PublicReviewDecisionRequest request) {
        ResolvedReviewLink resolved = resolveReviewLink(token);
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
    public PublicPostCollectionReviewResponse rejectFromClient(String token, PublicReviewDecisionRequest request) {
        ResolvedReviewLink resolved = resolveReviewLink(token);
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

    private ResolvedReviewLink resolveReviewLink(String token) {
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

        List<PublicReviewChannelResponse> channels = collection.getPosts() != null
                ? collection.getPosts().stream()
                .map(post -> {
                    ConnectedAccount account = accountsByProviderUserId.get(post.getProviderUserId());
                    return new PublicReviewChannelResponse(
                            post.getProvider() != null ? post.getProvider().name() : null,
                            account != null ? account.getUsername() : null,
                            account != null ? account.getProfilePicLink() : null
                    );
                })
                .toList()
                : List.of();

        List<MediaResponse> media = collection.getMediaFiles() != null
                ? collection.getMediaFiles().stream()
                .map(this::toMediaResponse)
                .toList()
                : List.of();

        Map<String, Object> platformConfigs = null;
        if (collection.getPlatformConfigs() != null && !collection.getPlatformConfigs().isBlank()) {
            try {
                platformConfigs = objectMapper.readValue(collection.getPlatformConfigs(), Map.class);
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
        boolean canComment = activeLink && collection.isDraft();
        boolean canApprove = activeLink
                && collection.isDraft()
                && reviewStatus == PostReviewStatus.IN_REVIEW
                && collection.getNextApprovalStage() != PostApprovalStage.OWNER_FINAL;
        boolean canReject = activeLink
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

    private record ReviewerIdentity(String displayName, String email) {
    }

    private record ResolvedReviewLink(PostCollectionReviewLinkEntity link,
                                      PostCollectionEntity collection,
                                      OffsetDateTime tokenExpiresAt) {
    }
}
