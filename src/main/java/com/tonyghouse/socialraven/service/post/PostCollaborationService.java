package com.tonyghouse.socialraven.service.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.constant.PostActorType;
import com.tonyghouse.socialraven.constant.PostCollectionVersionEvent;
import com.tonyghouse.socialraven.constant.PostCollaborationSuggestionStatus;
import com.tonyghouse.socialraven.constant.PostCollaborationThreadStatus;
import com.tonyghouse.socialraven.constant.PostCollaborationThreadType;
import com.tonyghouse.socialraven.constant.PostCollaborationVisibility;
import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.dto.PostCollaborationMentionResponse;
import com.tonyghouse.socialraven.dto.PostCollaborationReplyRequest;
import com.tonyghouse.socialraven.dto.PostCollaborationReplyResponse;
import com.tonyghouse.socialraven.dto.PostCollaborationThreadRequest;
import com.tonyghouse.socialraven.dto.PostCollaborationThreadResponse;
import com.tonyghouse.socialraven.entity.PostCollaborationReplyEntity;
import com.tonyghouse.socialraven.entity.PostCollaborationThreadEntity;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostMediaEntity;
import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.PostCollaborationReplyRepo;
import com.tonyghouse.socialraven.repo.PostCollaborationThreadRepo;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostCollaborationService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final Pattern REVIEWER_EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Autowired
    private PostCollectionRepo postCollectionRepo;

    @Autowired
    private PostCollaborationThreadRepo postCollaborationThreadRepo;

    @Autowired
    private PostCollaborationReplyRepo postCollaborationReplyRepo;

    @Autowired
    private WorkspaceMemberRepo workspaceMemberRepo;

    @Autowired
    private ClerkUserService clerkUserService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PostCollectionVersionService postCollectionVersionService;

    @Transactional(readOnly = true)
    public List<PostCollaborationThreadResponse> getThreads(String userId, Long collectionId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = requireCollection(collectionId, workspaceId);
        List<PostCollaborationThreadEntity> threads = postCollaborationThreadRepo.findAllByPostCollectionId(collection.getId());
        return buildThreadResponses(workspaceId, threads);
    }

    @Transactional(readOnly = true)
    public List<PostCollaborationThreadResponse> getClientVisibleThreads(Long collectionId, String workspaceId) {
        requireCollection(collectionId, workspaceId);
        List<PostCollaborationThreadEntity> threads = postCollaborationThreadRepo.findAllByPostCollectionId(collectionId).stream()
                .filter(thread -> thread.getVisibility() == PostCollaborationVisibility.CLIENT_VISIBLE)
                .toList();
        return buildThreadResponses(workspaceId, threads);
    }

    @Transactional
    public PostCollaborationThreadResponse createThread(String userId,
                                                        Long collectionId,
                                                        PostCollaborationThreadRequest request) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = requireCollection(collectionId, workspaceId);
        WorkspaceMembersSnapshot workspaceMembers = getWorkspaceMembers(workspaceId);

        if (request == null || request.getThreadType() == null) {
            throw new SocialRavenException("threadType is required", HttpStatus.BAD_REQUEST);
        }

        PostCollaborationThreadEntity thread = new PostCollaborationThreadEntity();
        thread.setPostCollectionId(collection.getId());
        thread.setWorkspaceId(workspaceId);
        thread.setThreadType(request.getThreadType());
        thread.setVisibility(resolveRequestedVisibility(request));
        thread.setStatus(PostCollaborationThreadStatus.OPEN);
        thread.setAuthorType(PostActorType.WORKSPACE_USER);
        thread.setAuthorUserId(userId);
        thread.setAuthorDisplayName(null);
        thread.setAuthorEmail(null);
        thread.setMentionedUserIds(writeMentionUserIds(normalizeMentionUserIds(
                request.getMentionUserIds(),
                workspaceMembers.memberIds(),
                thread.getVisibility()
        )));

        OffsetDateTime now = OffsetDateTime.now();
        thread.setCreatedAt(now);
        thread.setUpdatedAt(now);

        switch (request.getThreadType()) {
            case COMMENT, NOTE -> {
                thread.setBody(requireTrimmedBody(request.getBody()));
                thread.setSuggestionStatus(null);
                thread.setSuggestedText(null);
                applyOptionalAnnotation(
                        thread,
                        collection,
                        request.getAnchorStart(),
                        request.getAnchorEnd(),
                        request.getAnchorText(),
                        request.getMediaId(),
                        request.getMediaMarkerX(),
                        request.getMediaMarkerY()
                );
            }
            case SUGGESTION -> {
                thread.setBody(normalizeOptionalText(request.getBody()));
                thread.setSuggestionStatus(PostCollaborationSuggestionStatus.PENDING);
                thread.setSuggestedText(requireNonBlank(request.getSuggestedText(), "suggestedText is required"));
                if (request.getMediaId() != null
                        || request.getMediaMarkerX() != null
                        || request.getMediaMarkerY() != null) {
                    throw new SocialRavenException(
                            "Suggestions only support caption annotations",
                            HttpStatus.BAD_REQUEST
                    );
                }
                clearMediaAnnotation(thread);
                applyCaptionAnnotation(
                        thread,
                        collection.getDescription(),
                        request.getAnchorStart(),
                        request.getAnchorEnd(),
                        request.getAnchorText(),
                        true
                );
            }
        }

        PostCollaborationThreadEntity saved = postCollaborationThreadRepo.save(thread);
        return buildSingleThreadResponse(workspaceId, saved);
    }

    @Transactional
    public PostCollaborationThreadResponse createClientVisibleComment(Long collectionId,
                                                                      String workspaceId,
                                                                      String reviewerName,
                                                                      String reviewerEmail,
                                                                      String body,
                                                                      Integer anchorStart,
                                                                      Integer anchorEnd,
                                                                      String anchorText,
                                                                      Long mediaId,
                                                                      Double mediaMarkerX,
                                                                      Double mediaMarkerY) {
        PostCollectionEntity collection = requireCollection(collectionId, workspaceId);
        assertCollectionCommentable(collection);
        ClientReviewerIdentity reviewerIdentity = requireClientReviewerIdentity(reviewerName, reviewerEmail);

        PostCollaborationThreadEntity thread = new PostCollaborationThreadEntity();
        thread.setPostCollectionId(collection.getId());
        thread.setWorkspaceId(workspaceId);
        thread.setThreadType(PostCollaborationThreadType.COMMENT);
        thread.setVisibility(PostCollaborationVisibility.CLIENT_VISIBLE);
        thread.setStatus(PostCollaborationThreadStatus.OPEN);
        thread.setAuthorType(PostActorType.CLIENT_REVIEWER);
        thread.setAuthorUserId(null);
        thread.setAuthorDisplayName(reviewerIdentity.displayName());
        thread.setAuthorEmail(reviewerIdentity.email());
        thread.setBody(requireTrimmedBody(body));
        thread.setMentionedUserIds(writeMentionUserIds(List.of()));
        thread.setSuggestionStatus(null);
        thread.setSuggestedText(null);
        applyOptionalAnnotation(
                thread,
                collection,
                anchorStart,
                anchorEnd,
                anchorText,
                mediaId,
                mediaMarkerX,
                mediaMarkerY
        );

        OffsetDateTime now = OffsetDateTime.now();
        thread.setCreatedAt(now);
        thread.setUpdatedAt(now);

        PostCollaborationThreadEntity saved = postCollaborationThreadRepo.save(thread);
        return buildSingleThreadResponse(workspaceId, saved);
    }

    @Transactional
    public PostCollaborationThreadResponse addReply(String userId,
                                                    Long collectionId,
                                                    Long threadId,
                                                    PostCollaborationReplyRequest request) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        requireCollection(collectionId, workspaceId);
        PostCollaborationThreadEntity thread = requireThread(collectionId, threadId, workspaceId);
        WorkspaceMembersSnapshot workspaceMembers = getWorkspaceMembers(workspaceId);

        if (thread.getStatus() == PostCollaborationThreadStatus.RESOLVED) {
            throw new SocialRavenException("Reopen the thread before adding replies", HttpStatus.CONFLICT);
        }

        PostCollaborationReplyEntity reply = new PostCollaborationReplyEntity();
        reply.setThreadId(thread.getId());
        reply.setAuthorUserId(userId);
        reply.setBody(requireTrimmedBody(request != null ? request.getBody() : null));
        reply.setMentionedUserIds(writeMentionUserIds(normalizeMentionUserIds(
                request != null ? request.getMentionUserIds() : null,
                workspaceMembers.memberIds(),
                thread.getVisibility()
        )));
        OffsetDateTime now = OffsetDateTime.now();
        reply.setCreatedAt(now);
        reply.setUpdatedAt(now);
        postCollaborationReplyRepo.save(reply);

        thread.setUpdatedAt(now);
        postCollaborationThreadRepo.save(thread);
        return buildSingleThreadResponse(workspaceId, thread);
    }

    @Transactional
    public PostCollaborationThreadResponse resolveThread(String userId, Long collectionId, Long threadId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        requireCollection(collectionId, workspaceId);
        PostCollaborationThreadEntity thread = requireThread(collectionId, threadId, workspaceId);
        assertNotSuggestionResolutionAction(thread);

        if (thread.getStatus() == PostCollaborationThreadStatus.RESOLVED) {
            return buildSingleThreadResponse(workspaceId, thread);
        }

        OffsetDateTime now = OffsetDateTime.now();
        thread.setStatus(PostCollaborationThreadStatus.RESOLVED);
        thread.setResolvedAt(now);
        thread.setResolvedByUserId(userId);
        thread.setUpdatedAt(now);
        postCollaborationThreadRepo.save(thread);
        return buildSingleThreadResponse(workspaceId, thread);
    }

    @Transactional
    public PostCollaborationThreadResponse reopenThread(String userId, Long collectionId, Long threadId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        requireCollection(collectionId, workspaceId);
        PostCollaborationThreadEntity thread = requireThread(collectionId, threadId, workspaceId);
        assertNotSuggestionResolutionAction(thread);

        if (thread.getStatus() == PostCollaborationThreadStatus.OPEN) {
            return buildSingleThreadResponse(workspaceId, thread);
        }

        thread.setStatus(PostCollaborationThreadStatus.OPEN);
        thread.setResolvedAt(null);
        thread.setResolvedByUserId(null);
        thread.setUpdatedAt(OffsetDateTime.now());
        postCollaborationThreadRepo.save(thread);
        return buildSingleThreadResponse(workspaceId, thread);
    }

    @Transactional
    public PostCollaborationThreadResponse acceptSuggestion(String userId, Long collectionId, Long threadId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        PostCollectionEntity collection = requireCollection(collectionId, workspaceId);
        PostCollaborationThreadEntity thread = requireThread(collectionId, threadId, workspaceId);
        assertSuggestionPending(thread);
        assertCollectionEditableForSuggestion(collection);

        String currentDescription = collection.getDescription() != null ? collection.getDescription() : "";
        String nextDescription = applySuggestionToDescription(currentDescription, thread);
        collection.setDescription(nextDescription);
        PostCollectionEntity savedCollection = postCollectionRepo.save(collection);
        postCollectionVersionService.recordVersion(savedCollection, PostCollectionVersionEvent.UPDATED, userId);

        OffsetDateTime now = OffsetDateTime.now();
        thread.setSuggestionStatus(PostCollaborationSuggestionStatus.ACCEPTED);
        thread.setSuggestionDecidedBy(userId);
        thread.setSuggestionDecidedAt(now);
        thread.setStatus(PostCollaborationThreadStatus.RESOLVED);
        thread.setResolvedByUserId(userId);
        thread.setResolvedAt(now);
        thread.setUpdatedAt(now);
        postCollaborationThreadRepo.save(thread);
        return buildSingleThreadResponse(workspaceId, thread);
    }

    @Transactional
    public PostCollaborationThreadResponse rejectSuggestion(String userId, Long collectionId, Long threadId) {
        String workspaceId = WorkspaceContext.getWorkspaceId();
        requireCollection(collectionId, workspaceId);
        PostCollaborationThreadEntity thread = requireThread(collectionId, threadId, workspaceId);
        assertSuggestionPending(thread);

        OffsetDateTime now = OffsetDateTime.now();
        thread.setSuggestionStatus(PostCollaborationSuggestionStatus.REJECTED);
        thread.setSuggestionDecidedBy(userId);
        thread.setSuggestionDecidedAt(now);
        thread.setStatus(PostCollaborationThreadStatus.RESOLVED);
        thread.setResolvedByUserId(userId);
        thread.setResolvedAt(now);
        thread.setUpdatedAt(now);
        postCollaborationThreadRepo.save(thread);
        return buildSingleThreadResponse(workspaceId, thread);
    }

    private void applyCaptionAnnotation(PostCollaborationThreadEntity thread,
                                        String description,
                                        Integer anchorStart,
                                        Integer anchorEnd,
                                        String anchorText,
                                        boolean annotationRequired) {
        String currentDescription = description != null ? description : "";
        boolean annotationProvided =
                anchorStart != null || anchorEnd != null || anchorText != null;
        if (!annotationProvided) {
            if (annotationRequired) {
                throw new SocialRavenException(
                        "anchorStart and anchorEnd are required for caption annotations",
                        HttpStatus.BAD_REQUEST
                );
            }
            clearCaptionAnnotation(thread);
            return;
        }
        if (anchorStart == null || anchorEnd == null) {
            throw new SocialRavenException(
                    "anchorStart and anchorEnd are required for caption annotations",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (anchorStart < 0 || anchorEnd < anchorStart || anchorEnd > currentDescription.length()) {
            throw new SocialRavenException("Invalid caption annotation range", HttpStatus.BAD_REQUEST);
        }

        String currentSelection = currentDescription.substring(anchorStart, anchorEnd);
        if (anchorText == null || !currentSelection.equals(anchorText)) {
            throw new SocialRavenException(
                    "Caption annotation text does not match the current caption",
                    HttpStatus.CONFLICT
            );
        }

        thread.setAnchorStart(anchorStart);
        thread.setAnchorEnd(anchorEnd);
        thread.setAnchorText(anchorText);
        clearMediaAnnotation(thread);
    }

    private void applyOptionalAnnotation(PostCollaborationThreadEntity thread,
                                         PostCollectionEntity collection,
                                         Integer anchorStart,
                                         Integer anchorEnd,
                                         String anchorText,
                                         Long mediaId,
                                         Double mediaMarkerX,
                                         Double mediaMarkerY) {
        boolean captionAnnotationProvided =
                anchorStart != null || anchorEnd != null || anchorText != null;
        boolean mediaAnnotationProvided =
                mediaId != null || mediaMarkerX != null || mediaMarkerY != null;

        if (captionAnnotationProvided && mediaAnnotationProvided) {
            throw new SocialRavenException(
                    "Choose either a caption annotation or a media annotation, not both",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (captionAnnotationProvided) {
            applyCaptionAnnotation(
                    thread,
                    collection.getDescription(),
                    anchorStart,
                    anchorEnd,
                    anchorText,
                    false
            );
            return;
        }

        if (mediaAnnotationProvided) {
            applyMediaAnnotation(thread, collection, mediaId, mediaMarkerX, mediaMarkerY);
            return;
        }

        clearCaptionAnnotation(thread);
        clearMediaAnnotation(thread);
    }

    private void applyMediaAnnotation(PostCollaborationThreadEntity thread,
                                      PostCollectionEntity collection,
                                      Long mediaId,
                                      Double mediaMarkerX,
                                      Double mediaMarkerY) {
        if (mediaId == null || mediaMarkerX == null || mediaMarkerY == null) {
            throw new SocialRavenException(
                    "mediaId, mediaMarkerX, and mediaMarkerY are required for media annotations",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (mediaMarkerX < 0 || mediaMarkerX > 1 || mediaMarkerY < 0 || mediaMarkerY > 1) {
            throw new SocialRavenException(
                    "Media annotation coordinates must be between 0 and 1",
                    HttpStatus.BAD_REQUEST
            );
        }

        boolean mediaFound = collection.getMediaFiles() != null
                && collection.getMediaFiles().stream()
                .map(PostMediaEntity::getId)
                .anyMatch(id -> id != null && id.equals(mediaId));
        if (!mediaFound) {
            throw new SocialRavenException(
                    "Media annotations must target an existing collection asset",
                    HttpStatus.BAD_REQUEST
            );
        }

        clearCaptionAnnotation(thread);
        thread.setMediaId(mediaId);
        thread.setMediaMarkerX(mediaMarkerX);
        thread.setMediaMarkerY(mediaMarkerY);
    }

    private void clearCaptionAnnotation(PostCollaborationThreadEntity thread) {
        thread.setAnchorStart(null);
        thread.setAnchorEnd(null);
        thread.setAnchorText(null);
    }

    private void clearMediaAnnotation(PostCollaborationThreadEntity thread) {
        thread.setMediaId(null);
        thread.setMediaMarkerX(null);
        thread.setMediaMarkerY(null);
    }

    private String applySuggestionToDescription(String currentDescription, PostCollaborationThreadEntity thread) {
        Integer anchorStart = thread.getAnchorStart();
        Integer anchorEnd = thread.getAnchorEnd();
        String anchorText = thread.getAnchorText();
        String suggestedText = thread.getSuggestedText();
        if (anchorStart == null || anchorEnd == null || anchorText == null || suggestedText == null) {
            throw new SocialRavenException("Suggestion is missing anchor data", HttpStatus.CONFLICT);
        }
        if (anchorStart < 0 || anchorEnd < anchorStart || anchorEnd > currentDescription.length()) {
            throw new SocialRavenException("Caption changed after the suggestion was created", HttpStatus.CONFLICT);
        }

        String currentSelection = currentDescription.substring(anchorStart, anchorEnd);
        if (!currentSelection.equals(anchorText)) {
            throw new SocialRavenException("Caption changed after the suggestion was created", HttpStatus.CONFLICT);
        }

        return currentDescription.substring(0, anchorStart) + suggestedText + currentDescription.substring(anchorEnd);
    }

    private void assertCollectionEditableForSuggestion(PostCollectionEntity collection) {
        PostReviewStatus reviewStatus = collection.getReviewStatus() != null
                ? collection.getReviewStatus()
                : PostReviewStatus.DRAFT;
        if (reviewStatus == PostReviewStatus.IN_REVIEW || reviewStatus == PostReviewStatus.APPROVED || !collection.isDraft()) {
            throw new SocialRavenException(
                    "Suggestions can only be applied while the collection is editable",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void assertSuggestionPending(PostCollaborationThreadEntity thread) {
        if (thread.getThreadType() != PostCollaborationThreadType.SUGGESTION) {
            throw new SocialRavenException("This thread is not a suggestion", HttpStatus.BAD_REQUEST);
        }
        if (thread.getSuggestionStatus() != PostCollaborationSuggestionStatus.PENDING
                || thread.getStatus() != PostCollaborationThreadStatus.OPEN) {
            throw new SocialRavenException("This suggestion has already been resolved", HttpStatus.CONFLICT);
        }
    }

    private void assertNotSuggestionResolutionAction(PostCollaborationThreadEntity thread) {
        if (thread.getThreadType() == PostCollaborationThreadType.SUGGESTION) {
            throw new SocialRavenException(
                    "Suggestions must be accepted or rejected instead of resolved manually",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private PostCollectionEntity requireCollection(Long collectionId, String workspaceId) {
        PostCollectionEntity collection = postCollectionRepo.findById(collectionId)
                .orElseThrow(() -> new SocialRavenException("Post collection not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(collection.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        return collection;
    }

    private PostCollaborationThreadEntity requireThread(Long collectionId, Long threadId, String workspaceId) {
        PostCollaborationThreadEntity thread = postCollaborationThreadRepo.findByIdAndPostCollectionId(threadId, collectionId)
                .orElseThrow(() -> new SocialRavenException("Collaboration thread not found", HttpStatus.NOT_FOUND));
        if (!workspaceId.equals(thread.getWorkspaceId())) {
            throw new SocialRavenException("Access denied", HttpStatus.FORBIDDEN);
        }
        return thread;
    }

    private WorkspaceMembersSnapshot getWorkspaceMembers(String workspaceId) {
        List<WorkspaceMemberEntity> members = workspaceMemberRepo.findAllByWorkspaceId(workspaceId);
        Set<String> memberIds = members.stream()
                .map(WorkspaceMemberEntity::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new WorkspaceMembersSnapshot(members, memberIds);
    }

    private List<String> normalizeMentionUserIds(List<String> mentionUserIds,
                                                 Set<String> workspaceMemberIds,
                                                 PostCollaborationVisibility visibility) {
        if (visibility == PostCollaborationVisibility.CLIENT_VISIBLE) {
            return List.of();
        }
        if (mentionUserIds == null || mentionUserIds.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String userId : mentionUserIds) {
            if (userId == null) {
                continue;
            }
            String trimmed = userId.trim();
            if (trimmed.isEmpty() || !seen.add(trimmed)) {
                continue;
            }
            if (!workspaceMemberIds.contains(trimmed)) {
                throw new SocialRavenException("Mentioned user is not a workspace member", HttpStatus.BAD_REQUEST);
            }
            normalized.add(trimmed);
        }
        return normalized;
    }

    private List<PostCollaborationThreadResponse> buildThreadResponses(String workspaceId,
                                                                       List<PostCollaborationThreadEntity> threads) {
        if (threads == null || threads.isEmpty()) {
            return List.of();
        }

        Map<Long, List<PostCollaborationReplyEntity>> repliesByThreadId = loadRepliesByThreadId(threads);
        Map<String, String> displayNames = resolveDisplayNames(collectRelevantUserIds(threads, repliesByThreadId.values()));

        List<PostCollaborationThreadEntity> orderedThreads = new ArrayList<>(threads);
        orderedThreads.sort(
                Comparator.comparing((PostCollaborationThreadEntity thread) ->
                                thread.getStatus() == PostCollaborationThreadStatus.OPEN ? 0 : 1)
                        .thenComparing(PostCollaborationThreadEntity::getUpdatedAt, Comparator.reverseOrder())
                        .thenComparing(PostCollaborationThreadEntity::getId, Comparator.reverseOrder())
        );

        return orderedThreads.stream()
                .map(thread -> toThreadResponse(thread, repliesByThreadId.getOrDefault(thread.getId(), List.of()), displayNames))
                .toList();
    }

    private PostCollaborationThreadResponse buildSingleThreadResponse(String workspaceId,
                                                                      PostCollaborationThreadEntity thread) {
        List<PostCollaborationReplyEntity> replies = postCollaborationReplyRepo.findAllByThreadIdInOrderByCreatedAtAsc(List.of(thread.getId()));
        Map<String, String> displayNames = resolveDisplayNames(collectRelevantUserIds(List.of(thread), List.of(replies)));
        return toThreadResponse(thread, replies, displayNames);
    }

    private Map<Long, List<PostCollaborationReplyEntity>> loadRepliesByThreadId(List<PostCollaborationThreadEntity> threads) {
        List<Long> threadIds = threads.stream()
                .map(PostCollaborationThreadEntity::getId)
                .toList();
        if (threadIds.isEmpty()) {
            return Map.of();
        }

        return postCollaborationReplyRepo.findAllByThreadIdInOrderByCreatedAtAsc(threadIds).stream()
                .collect(Collectors.groupingBy(
                        PostCollaborationReplyEntity::getThreadId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Set<String> collectRelevantUserIds(List<PostCollaborationThreadEntity> threads,
                                               Collection<List<PostCollaborationReplyEntity>> repliesCollection) {
        Set<String> userIds = new LinkedHashSet<>();

        for (PostCollaborationThreadEntity thread : threads) {
            addUserId(userIds, thread.getAuthorUserId());
            addUserId(userIds, thread.getResolvedByUserId());
            addUserId(userIds, thread.getSuggestionDecidedBy());
            readMentionUserIds(thread.getMentionedUserIds()).forEach(mentionUserId -> addUserId(userIds, mentionUserId));
        }

        for (List<PostCollaborationReplyEntity> replies : repliesCollection) {
            for (PostCollaborationReplyEntity reply : replies) {
                addUserId(userIds, reply.getAuthorUserId());
                readMentionUserIds(reply.getMentionedUserIds()).forEach(mentionUserId -> addUserId(userIds, mentionUserId));
            }
        }

        return userIds;
    }

    private void addUserId(Set<String> userIds, String userId) {
        if (userId != null && !userId.isBlank()) {
            userIds.add(userId);
        }
    }

    private Map<String, String> resolveDisplayNames(Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Map<String, String> displayNames = new LinkedHashMap<>();
        for (String userId : userIds) {
            displayNames.put(userId, resolveDisplayName(userId));
        }
        return displayNames;
    }

    private PostCollaborationThreadResponse toThreadResponse(PostCollaborationThreadEntity thread,
                                                             List<PostCollaborationReplyEntity> replies,
                                                             Map<String, String> displayNames) {
        return new PostCollaborationThreadResponse(
                thread.getId(),
                thread.getThreadType().name(),
                thread.getVisibility().name(),
                thread.getStatus().name(),
                thread.getAuthorType() != null ? thread.getAuthorType().name() : PostActorType.WORKSPACE_USER.name(),
                thread.getAuthorUserId(),
                resolveThreadAuthorDisplayName(thread, displayNames),
                thread.getBody(),
                toMentionResponses(readMentionUserIds(thread.getMentionedUserIds()), displayNames),
                thread.getAnchorStart(),
                thread.getAnchorEnd(),
                thread.getAnchorText(),
                thread.getMediaId(),
                thread.getMediaMarkerX(),
                thread.getMediaMarkerY(),
                thread.getSuggestedText(),
                thread.getSuggestionStatus() != null ? thread.getSuggestionStatus().name() : null,
                thread.getSuggestionDecidedBy(),
                thread.getSuggestionDecidedBy() != null
                        ? displayNames.getOrDefault(thread.getSuggestionDecidedBy(), thread.getSuggestionDecidedBy())
                        : null,
                thread.getSuggestionDecidedAt(),
                thread.getResolvedByUserId(),
                thread.getResolvedByUserId() != null
                        ? displayNames.getOrDefault(thread.getResolvedByUserId(), thread.getResolvedByUserId())
                        : null,
                thread.getResolvedAt(),
                thread.getCreatedAt(),
                thread.getUpdatedAt(),
                replies.stream().map(reply -> toReplyResponse(reply, displayNames)).toList()
        );
    }

    private PostCollaborationReplyResponse toReplyResponse(PostCollaborationReplyEntity reply,
                                                           Map<String, String> displayNames) {
        return new PostCollaborationReplyResponse(
                reply.getId(),
                reply.getAuthorUserId(),
                displayNames.getOrDefault(reply.getAuthorUserId(), reply.getAuthorUserId()),
                reply.getBody(),
                toMentionResponses(readMentionUserIds(reply.getMentionedUserIds()), displayNames),
                reply.getCreatedAt(),
                reply.getUpdatedAt()
        );
    }

    private List<PostCollaborationMentionResponse> toMentionResponses(List<String> mentionUserIds,
                                                                      Map<String, String> displayNames) {
        if (mentionUserIds == null || mentionUserIds.isEmpty()) {
            return List.of();
        }

        return mentionUserIds.stream()
                .map(userId -> new PostCollaborationMentionResponse(
                        userId,
                        displayNames.getOrDefault(userId, userId)
                ))
                .toList();
    }

    private List<String> readMentionUserIds(String mentionedUserIdsJson) {
        if (mentionedUserIdsJson == null || mentionedUserIdsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> userIds = objectMapper.readValue(mentionedUserIdsJson, STRING_LIST_TYPE);
            return userIds != null ? userIds : List.of();
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private String writeMentionUserIds(List<String> mentionUserIds) {
        try {
            return objectMapper.writeValueAsString(mentionUserIds != null ? mentionUserIds : List.of());
        } catch (JsonProcessingException e) {
            throw new SocialRavenException("Failed to store collaboration mentions", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String requireTrimmedBody(String body) {
        return requireNonBlank(body, "body is required");
    }

    private String requireNonBlank(String value, String message) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new SocialRavenException(message, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveDisplayName(String userId) {
        ClerkUserService.UserProfile profile = clerkUserService.getUserProfile(userId);
        if (profile == null) {
            return userId;
        }

        String firstName = profile.firstName() != null ? profile.firstName().trim() : "";
        String lastName = profile.lastName() != null ? profile.lastName().trim() : "";
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        if (profile.email() != null && !profile.email().isBlank()) {
            return profile.email().trim();
        }
        return userId;
    }

    private String resolveThreadAuthorDisplayName(PostCollaborationThreadEntity thread,
                                                  Map<String, String> displayNames) {
        if (thread.getAuthorType() == PostActorType.CLIENT_REVIEWER) {
            if (thread.getAuthorDisplayName() != null && !thread.getAuthorDisplayName().isBlank()) {
                return thread.getAuthorDisplayName().trim();
            }
            if (thread.getAuthorEmail() != null && !thread.getAuthorEmail().isBlank()) {
                return thread.getAuthorEmail().trim();
            }
            return "Client reviewer";
        }
        return displayNames.getOrDefault(thread.getAuthorUserId(), thread.getAuthorUserId());
    }

    private PostCollaborationVisibility resolveRequestedVisibility(PostCollaborationThreadRequest request) {
        PostCollaborationVisibility requestedVisibility = request.getVisibility() != null
                ? request.getVisibility()
                : PostCollaborationVisibility.INTERNAL;
        if (requestedVisibility == PostCollaborationVisibility.CLIENT_VISIBLE
                && request.getThreadType() != PostCollaborationThreadType.COMMENT) {
            throw new SocialRavenException(
                    "Only comment threads can be visible to client reviewers",
                    HttpStatus.BAD_REQUEST
            );
        }
        return requestedVisibility;
    }

    private void assertCollectionCommentable(PostCollectionEntity collection) {
        if (!collection.isDraft()) {
            throw new SocialRavenException(
                    "Client review comments are only available while this collection is still in workflow",
                    HttpStatus.CONFLICT
            );
        }
    }

    private ClientReviewerIdentity requireClientReviewerIdentity(String reviewerName, String reviewerEmail) {
        String displayName = requireNonBlank(reviewerName, "reviewerName is required");
        String email = requireNonBlank(reviewerEmail, "reviewerEmail is required").toLowerCase();
        if (!REVIEWER_EMAIL_PATTERN.matcher(email).matches()) {
            throw new SocialRavenException("reviewerEmail must be a valid email address", HttpStatus.BAD_REQUEST);
        }
        return new ClientReviewerIdentity(displayName, email);
    }

    private record WorkspaceMembersSnapshot(List<WorkspaceMemberEntity> members, Set<String> memberIds) {
        private WorkspaceMembersSnapshot {
            members = members != null ? members : Collections.emptyList();
            memberIds = memberIds != null ? memberIds : Collections.emptySet();
        }
    }

    private record ClientReviewerIdentity(String displayName, String email) {
    }
}
