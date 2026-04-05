package com.tonyghouse.socialraven.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.constant.PostActorType;
import com.tonyghouse.socialraven.constant.PostCollaborationThreadType;
import com.tonyghouse.socialraven.constant.PostCollaborationVisibility;
import com.tonyghouse.socialraven.constant.PostCollectionType;
import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.PostCollaborationThreadRequest;
import com.tonyghouse.socialraven.entity.PostCollaborationThreadEntity;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostMediaEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.PostCollaborationReplyRepo;
import com.tonyghouse.socialraven.repo.PostCollaborationThreadRepo;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PostCollaborationServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void createThreadStoresCaptionAnnotationForComment() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollaborationThreadRepo postCollaborationThreadRepo = mock(PostCollaborationThreadRepo.class);
        PostCollaborationReplyRepo postCollaborationReplyRepo = mock(PostCollaborationReplyRepo.class);
        WorkspaceMemberRepo workspaceMemberRepo = mock(WorkspaceMemberRepo.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);

        PostCollaborationService service = createService(
                postCollectionRepo,
                postCollaborationThreadRepo,
                postCollaborationReplyRepo,
                workspaceMemberRepo,
                clerkUserService,
                postCollectionVersionService
        );

        PostCollectionEntity collection = createCollection("workspace_1", "Hello world", List.of());
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(workspaceMemberRepo.findAllByWorkspaceId("workspace_1")).thenReturn(List.of());
        when(clerkUserService.getUserProfile("user_123"))
                .thenReturn(new ClerkUserService.UserProfile("Jane", "Editor", "jane@example.com"));
        when(postCollaborationReplyRepo.findAllByThreadIdInOrderByCreatedAtAsc(anyList()))
                .thenReturn(List.of());
        when(postCollaborationThreadRepo.save(any(PostCollaborationThreadEntity.class)))
                .thenAnswer(invocation -> {
                    PostCollaborationThreadEntity thread = invocation.getArgument(0);
                    thread.setId(10L);
                    return thread;
                });

        PostCollaborationThreadRequest request = new PostCollaborationThreadRequest();
        request.setThreadType(PostCollaborationThreadType.COMMENT);
        request.setVisibility(PostCollaborationVisibility.INTERNAL);
        request.setBody("Please tighten this opening line");
        request.setAnchorStart(0);
        request.setAnchorEnd(5);
        request.setAnchorText("Hello");

        WorkspaceContext.set("workspace_1", WorkspaceRole.EDITOR);

        var response = service.createThread("user_123", 1L, request);

        ArgumentCaptor<PostCollaborationThreadEntity> threadCaptor =
                ArgumentCaptor.forClass(PostCollaborationThreadEntity.class);
        verify(postCollaborationThreadRepo).save(threadCaptor.capture());
        PostCollaborationThreadEntity savedThread = threadCaptor.getValue();

        assertThat(savedThread.getThreadType()).isEqualTo(PostCollaborationThreadType.COMMENT);
        assertThat(savedThread.getBody()).isEqualTo("Please tighten this opening line");
        assertThat(savedThread.getAnchorStart()).isZero();
        assertThat(savedThread.getAnchorEnd()).isEqualTo(5);
        assertThat(savedThread.getAnchorText()).isEqualTo("Hello");
        assertThat(savedThread.getMediaId()).isNull();
        assertThat(savedThread.getMediaMarkerX()).isNull();
        assertThat(savedThread.getMediaMarkerY()).isNull();

        assertThat(response.getThreadType()).isEqualTo("COMMENT");
        assertThat(response.getAuthorDisplayName()).isEqualTo("Jane Editor");
        assertThat(response.getAnchorText()).isEqualTo("Hello");
        assertThat(response.getMediaId()).isNull();
    }

    @Test
    void createThreadStoresMediaAnnotationForInternalNote() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollaborationThreadRepo postCollaborationThreadRepo = mock(PostCollaborationThreadRepo.class);
        PostCollaborationReplyRepo postCollaborationReplyRepo = mock(PostCollaborationReplyRepo.class);
        WorkspaceMemberRepo workspaceMemberRepo = mock(WorkspaceMemberRepo.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);

        PostCollaborationService service = createService(
                postCollectionRepo,
                postCollaborationThreadRepo,
                postCollaborationReplyRepo,
                workspaceMemberRepo,
                clerkUserService,
                postCollectionVersionService
        );

        PostMediaEntity media = createMedia(7L);
        PostCollectionEntity collection = createCollection("workspace_1", "Launch teaser", List.of(media));
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(workspaceMemberRepo.findAllByWorkspaceId("workspace_1")).thenReturn(List.of());
        when(clerkUserService.getUserProfile("user_123"))
                .thenReturn(new ClerkUserService.UserProfile("Jane", "Editor", "jane@example.com"));
        when(postCollaborationReplyRepo.findAllByThreadIdInOrderByCreatedAtAsc(anyList()))
                .thenReturn(List.of());
        when(postCollaborationThreadRepo.save(any(PostCollaborationThreadEntity.class)))
                .thenAnswer(invocation -> {
                    PostCollaborationThreadEntity thread = invocation.getArgument(0);
                    thread.setId(20L);
                    return thread;
                });

        PostCollaborationThreadRequest request = new PostCollaborationThreadRequest();
        request.setThreadType(PostCollaborationThreadType.NOTE);
        request.setBody("The brand mark needs breathing room");
        request.setMediaId(7L);
        request.setMediaMarkerX(0.25);
        request.setMediaMarkerY(0.75);

        WorkspaceContext.set("workspace_1", WorkspaceRole.EDITOR);

        var response = service.createThread("user_123", 1L, request);

        ArgumentCaptor<PostCollaborationThreadEntity> threadCaptor =
                ArgumentCaptor.forClass(PostCollaborationThreadEntity.class);
        verify(postCollaborationThreadRepo).save(threadCaptor.capture());
        PostCollaborationThreadEntity savedThread = threadCaptor.getValue();

        assertThat(savedThread.getThreadType()).isEqualTo(PostCollaborationThreadType.NOTE);
        assertThat(savedThread.getAnchorStart()).isNull();
        assertThat(savedThread.getAnchorEnd()).isNull();
        assertThat(savedThread.getAnchorText()).isNull();
        assertThat(savedThread.getMediaId()).isEqualTo(7L);
        assertThat(savedThread.getMediaMarkerX()).isEqualTo(0.25);
        assertThat(savedThread.getMediaMarkerY()).isEqualTo(0.75);

        assertThat(response.getThreadType()).isEqualTo("NOTE");
        assertThat(response.getMediaId()).isEqualTo(7L);
        assertThat(response.getMediaMarkerX()).isEqualTo(0.25);
        assertThat(response.getMediaMarkerY()).isEqualTo(0.75);
    }

    @Test
    void createThreadRejectsMixedCaptionAndMediaAnnotations() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollaborationThreadRepo postCollaborationThreadRepo = mock(PostCollaborationThreadRepo.class);
        PostCollaborationReplyRepo postCollaborationReplyRepo = mock(PostCollaborationReplyRepo.class);
        WorkspaceMemberRepo workspaceMemberRepo = mock(WorkspaceMemberRepo.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);

        PostCollaborationService service = createService(
                postCollectionRepo,
                postCollaborationThreadRepo,
                postCollaborationReplyRepo,
                workspaceMemberRepo,
                clerkUserService,
                postCollectionVersionService
        );

        PostMediaEntity media = createMedia(7L);
        PostCollectionEntity collection = createCollection("workspace_1", "Hello world", List.of(media));
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(workspaceMemberRepo.findAllByWorkspaceId("workspace_1")).thenReturn(List.of());

        PostCollaborationThreadRequest request = new PostCollaborationThreadRequest();
        request.setThreadType(PostCollaborationThreadType.COMMENT);
        request.setBody("This should fail");
        request.setAnchorStart(0);
        request.setAnchorEnd(5);
        request.setAnchorText("Hello");
        request.setMediaId(7L);
        request.setMediaMarkerX(0.2);
        request.setMediaMarkerY(0.3);

        WorkspaceContext.set("workspace_1", WorkspaceRole.EDITOR);

        assertThatThrownBy(() -> service.createThread("user_123", 1L, request))
                .isInstanceOf(SocialRavenException.class)
                .hasMessage("Choose either a caption annotation or a media annotation, not both")
                .extracting("errorCode")
                .isEqualTo("400");
    }

    @Test
    void createClientVisibleCommentStoresMediaAnnotationForReviewer() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollaborationThreadRepo postCollaborationThreadRepo = mock(PostCollaborationThreadRepo.class);
        PostCollaborationReplyRepo postCollaborationReplyRepo = mock(PostCollaborationReplyRepo.class);
        WorkspaceMemberRepo workspaceMemberRepo = mock(WorkspaceMemberRepo.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);
        PostCollectionVersionService postCollectionVersionService = mock(PostCollectionVersionService.class);

        PostCollaborationService service = createService(
                postCollectionRepo,
                postCollaborationThreadRepo,
                postCollaborationReplyRepo,
                workspaceMemberRepo,
                clerkUserService,
                postCollectionVersionService
        );

        PostMediaEntity media = createMedia(11L);
        PostCollectionEntity collection = createCollection("workspace_1", "Spring launch", List.of(media));
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(postCollaborationReplyRepo.findAllByThreadIdInOrderByCreatedAtAsc(anyList()))
                .thenReturn(List.of());
        when(postCollaborationThreadRepo.save(any(PostCollaborationThreadEntity.class)))
                .thenAnswer(invocation -> {
                    PostCollaborationThreadEntity thread = invocation.getArgument(0);
                    thread.setId(30L);
                    return thread;
                });

        var response = service.createClientVisibleComment(
                1L,
                "workspace_1",
                "Jane Client",
                "JANE@CLIENT.COM",
                "Please adjust this crop",
                null,
                null,
                null,
                11L,
                0.4,
                0.6
        );

        ArgumentCaptor<PostCollaborationThreadEntity> threadCaptor =
                ArgumentCaptor.forClass(PostCollaborationThreadEntity.class);
        verify(postCollaborationThreadRepo).save(threadCaptor.capture());
        PostCollaborationThreadEntity savedThread = threadCaptor.getValue();

        assertThat(savedThread.getAuthorType()).isEqualTo(PostActorType.CLIENT_REVIEWER);
        assertThat(savedThread.getVisibility()).isEqualTo(PostCollaborationVisibility.CLIENT_VISIBLE);
        assertThat(savedThread.getAuthorDisplayName()).isEqualTo("Jane Client");
        assertThat(savedThread.getAuthorEmail()).isEqualTo("jane@client.com");
        assertThat(savedThread.getMediaId()).isEqualTo(11L);
        assertThat(savedThread.getMediaMarkerX()).isEqualTo(0.4);
        assertThat(savedThread.getMediaMarkerY()).isEqualTo(0.6);

        assertThat(response.getAuthorType()).isEqualTo("CLIENT_REVIEWER");
        assertThat(response.getAuthorDisplayName()).isEqualTo("Jane Client");
        assertThat(response.getMediaId()).isEqualTo(11L);
    }

    private PostCollaborationService createService(PostCollectionRepo postCollectionRepo,
                                                   PostCollaborationThreadRepo postCollaborationThreadRepo,
                                                   PostCollaborationReplyRepo postCollaborationReplyRepo,
                                                   WorkspaceMemberRepo workspaceMemberRepo,
                                                   ClerkUserService clerkUserService,
                                                   PostCollectionVersionService postCollectionVersionService) {
        PostCollaborationService service = new PostCollaborationService();
        ReflectionTestUtils.setField(service, "postCollectionRepo", postCollectionRepo);
        ReflectionTestUtils.setField(service, "postCollaborationThreadRepo", postCollaborationThreadRepo);
        ReflectionTestUtils.setField(service, "postCollaborationReplyRepo", postCollaborationReplyRepo);
        ReflectionTestUtils.setField(service, "workspaceMemberRepo", workspaceMemberRepo);
        ReflectionTestUtils.setField(service, "clerkUserService", clerkUserService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "postCollectionVersionService", postCollectionVersionService);
        return service;
    }

    private PostCollectionEntity createCollection(String workspaceId,
                                                  String description,
                                                  List<PostMediaEntity> mediaFiles) {
        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setId(1L);
        collection.setWorkspaceId(workspaceId);
        collection.setCreatedBy("user_123");
        collection.setDescription(description);
        collection.setDraft(true);
        collection.setReviewStatus(PostReviewStatus.DRAFT);
        collection.setPostCollectionType(PostCollectionType.IMAGE);
        collection.setMediaFiles(mediaFiles);
        return collection;
    }

    private PostMediaEntity createMedia(Long mediaId) {
        PostMediaEntity media = new PostMediaEntity();
        media.setId(mediaId);
        media.setFileName("asset-" + mediaId + ".png");
        media.setMimeType("image/png");
        media.setFileKey("uploads/asset-" + mediaId + ".png");
        media.setSize(1234L);
        return media;
    }
}
