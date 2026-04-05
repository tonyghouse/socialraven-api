package com.tonyghouse.socialraven.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.constant.Platform;
import com.tonyghouse.socialraven.constant.PostApprovalStage;
import com.tonyghouse.socialraven.constant.PostReviewLinkShareScope;
import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.constant.PostStatus;
import com.tonyghouse.socialraven.constant.PostType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.WorkspaceCapability;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.dto.CreatePostCollectionReviewLinkRequest;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostCollectionReviewLinkEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.PostCollectionReviewLinkRepo;
import com.tonyghouse.socialraven.service.ClerkUserService;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import com.tonyghouse.socialraven.service.storage.StorageService;
import com.tonyghouse.socialraven.service.workspace.WorkspaceCapabilityService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class PostCollectionReviewLinkServiceTest {

    @AfterEach
    void tearDown() {
        WorkspaceContext.clear();
    }

    @Test
    void createReviewLinkStoresPasscodeHashAndSelectedPostScope() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewLinkRepo postCollectionReviewLinkRepo = mock(PostCollectionReviewLinkRepo.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        PostReviewLinkTokenService postReviewLinkTokenService = mock(PostReviewLinkTokenService.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);
        PostCollaborationService postCollaborationService = mock(PostCollaborationService.class);
        PostService postService = mock(PostService.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        StorageService storageService = mock(StorageService.class);

        PostCollectionReviewLinkService service = createService(
                postCollectionRepo,
                postCollectionReviewLinkRepo,
                workspaceCapabilityService,
                postReviewLinkTokenService,
                clerkUserService,
                postCollaborationService,
                postService,
                accountProfileService,
                storageService
        );

        PostCollectionEntity collection = createCollection(
                "workspace_1",
                List.of(createPost(101L, Provider.X, "account_x"), createPost(102L, Provider.LINKEDIN, "account_ln"))
        );
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(workspaceCapabilityService.hasCapability(
                "workspace_1",
                "user_123",
                WorkspaceRole.EDITOR,
                WorkspaceCapability.SHARE_REVIEW_LINKS
        )).thenReturn(true);
        when(postReviewLinkTokenService.generateToken(anyString(), any())).thenReturn("signed-token");
        when(clerkUserService.getUserProfile("user_123"))
                .thenReturn(new ClerkUserService.UserProfile("Sara", "Owner", "sara@example.com"));
        when(postCollectionReviewLinkRepo.save(any(PostCollectionReviewLinkEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreatePostCollectionReviewLinkRequest request = new CreatePostCollectionReviewLinkRequest();
        request.setExpiresAt(OffsetDateTime.now().plusDays(2));
        request.setPasscode("client-123");
        request.setShareScope(PostReviewLinkShareScope.SELECTED_POSTS);
        request.setSharedPostIds(List.of(101L));

        WorkspaceContext.set("workspace_1", WorkspaceRole.EDITOR);

        var response = service.createReviewLink("user_123", 1L, request);

        ArgumentCaptor<PostCollectionReviewLinkEntity> linkCaptor =
                ArgumentCaptor.forClass(PostCollectionReviewLinkEntity.class);
        verify(postCollectionReviewLinkRepo).save(linkCaptor.capture());
        PostCollectionReviewLinkEntity savedLink = linkCaptor.getValue();

        assertThat(savedLink.getShareScope()).isEqualTo(PostReviewLinkShareScope.SELECTED_POSTS);
        assertThat(savedLink.getSharedPostIds()).contains("101");
        assertThat(savedLink.getPasscodeHash()).isNotBlank().isNotEqualTo("client-123");
        assertThat(new BCryptPasswordEncoder().matches("client-123", savedLink.getPasscodeHash())).isTrue();

        assertThat(response.getShareScope()).isEqualTo("SELECTED_POSTS");
        assertThat(response.getSharedPostIds()).containsExactly(101L);
        assertThat(response.isPasscodeProtected()).isTrue();
    }

    @Test
    void getPublicReviewRejectsProtectedLinkWithoutPasscode() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewLinkRepo postCollectionReviewLinkRepo = mock(PostCollectionReviewLinkRepo.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        PostReviewLinkTokenService postReviewLinkTokenService = mock(PostReviewLinkTokenService.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);
        PostCollaborationService postCollaborationService = mock(PostCollaborationService.class);
        PostService postService = mock(PostService.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        StorageService storageService = mock(StorageService.class);

        PostCollectionReviewLinkService service = createService(
                postCollectionRepo,
                postCollectionReviewLinkRepo,
                workspaceCapabilityService,
                postReviewLinkTokenService,
                clerkUserService,
                postCollaborationService,
                postService,
                accountProfileService,
                storageService
        );

        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(1);
        PostCollectionEntity collection = createCollection("workspace_1", List.of(createPost(101L, Provider.X, "account_x")));
        PostCollectionReviewLinkEntity link = createReviewLink("link_1", "workspace_1", 1L, expiresAt);
        link.setPasscodeHash(new BCryptPasswordEncoder().encode("client-123"));

        when(postReviewLinkTokenService.parseAndValidate("signed-token"))
                .thenReturn(new PostReviewLinkTokenService.ValidatedReviewLinkToken("link_1", expiresAt));
        when(postCollectionReviewLinkRepo.findById("link_1")).thenReturn(Optional.of(link));
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> service.getPublicReview("signed-token", null))
                .isInstanceOf(SocialRavenException.class)
                .hasMessage("This review link requires a passcode")
                .extracting("errorCode")
                .isEqualTo("403");
    }

    @Test
    void getPublicReviewFiltersSelectedPostsAndDisablesApprovalActions() throws Exception {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewLinkRepo postCollectionReviewLinkRepo = mock(PostCollectionReviewLinkRepo.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        PostReviewLinkTokenService postReviewLinkTokenService = mock(PostReviewLinkTokenService.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);
        PostCollaborationService postCollaborationService = mock(PostCollaborationService.class);
        PostService postService = mock(PostService.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        StorageService storageService = mock(StorageService.class);

        PostCollectionReviewLinkService service = createService(
                postCollectionRepo,
                postCollectionReviewLinkRepo,
                workspaceCapabilityService,
                postReviewLinkTokenService,
                clerkUserService,
                postCollaborationService,
                postService,
                accountProfileService,
                storageService
        );

        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(1);
        PostEntity xPost = createPost(101L, Provider.X, "account_x");
        PostEntity linkedinPost = createPost(102L, Provider.LINKEDIN, "account_ln");
        PostCollectionEntity collection = createCollection("workspace_1", List.of(xPost, linkedinPost));
        collection.setReviewStatus(PostReviewStatus.IN_REVIEW);
        collection.setNextApprovalStage(PostApprovalStage.APPROVER);
        collection.setPlatformConfigs(new ObjectMapper().writeValueAsString(Map.of(
                "X", Map.of("tone", "short"),
                "LINKEDIN", Map.of("tone", "professional")
        )));

        PostCollectionReviewLinkEntity link = createReviewLink("link_1", "workspace_1", 1L, expiresAt);
        link.setShareScope(PostReviewLinkShareScope.SELECTED_POSTS);
        link.setSharedPostIds("[101]");
        link.setPasscodeHash(new BCryptPasswordEncoder().encode("client-123"));

        when(postReviewLinkTokenService.parseAndValidate("signed-token"))
                .thenReturn(new PostReviewLinkTokenService.ValidatedReviewLinkToken("link_1", expiresAt));
        when(postCollectionReviewLinkRepo.findById("link_1")).thenReturn(Optional.of(link));
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(postCollectionReviewLinkRepo.save(any(PostCollectionReviewLinkEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountProfileService.getAllConnectedAccounts(eq("workspace_1"), anyBoolean()))
                .thenReturn(List.of(
                        createConnectedAccount("account_x", Platform.x, "socialraven_x"),
                        createConnectedAccount("account_ln", Platform.linkedin, "SocialRaven LinkedIn")
                ));
        when(postCollaborationService.getClientVisibleThreads(1L, "workspace_1")).thenReturn(List.of());

        var response = service.getPublicReview("signed-token", "client-123");

        assertThat(response.getShareScope()).isEqualTo("SELECTED_POSTS");
        assertThat(response.getChannels()).hasSize(1);
        assertThat(response.getChannels().get(0).getPlatform()).isEqualTo("X");
        assertThat(response.getPlatformConfigs()).containsOnlyKeys("X");
        assertThat(response.isCanComment()).isTrue();
        assertThat(response.isCanApprove()).isFalse();
        assertThat(response.isCanReject()).isFalse();

        verify(postCollectionReviewLinkRepo).save(any(PostCollectionReviewLinkEntity.class));
    }

    @Test
    void getPublicReviewDisablesCommentsAfterFinalApproval() {
        PostCollectionRepo postCollectionRepo = mock(PostCollectionRepo.class);
        PostCollectionReviewLinkRepo postCollectionReviewLinkRepo = mock(PostCollectionReviewLinkRepo.class);
        WorkspaceCapabilityService workspaceCapabilityService = mock(WorkspaceCapabilityService.class);
        PostReviewLinkTokenService postReviewLinkTokenService = mock(PostReviewLinkTokenService.class);
        ClerkUserService clerkUserService = mock(ClerkUserService.class);
        PostCollaborationService postCollaborationService = mock(PostCollaborationService.class);
        PostService postService = mock(PostService.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);
        StorageService storageService = mock(StorageService.class);

        PostCollectionReviewLinkService service = createService(
                postCollectionRepo,
                postCollectionReviewLinkRepo,
                workspaceCapabilityService,
                postReviewLinkTokenService,
                clerkUserService,
                postCollaborationService,
                postService,
                accountProfileService,
                storageService
        );

        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(1);
        PostCollectionEntity collection = createCollection("workspace_1", List.of(createPost(101L, Provider.X, "account_x")));
        collection.setReviewStatus(PostReviewStatus.APPROVED);
        PostCollectionReviewLinkEntity link = createReviewLink("link_1", "workspace_1", 1L, expiresAt);

        when(postReviewLinkTokenService.parseAndValidate("signed-token"))
                .thenReturn(new PostReviewLinkTokenService.ValidatedReviewLinkToken("link_1", expiresAt));
        when(postCollectionReviewLinkRepo.findById("link_1")).thenReturn(Optional.of(link));
        when(postCollectionRepo.findById(1L)).thenReturn(Optional.of(collection));
        when(postCollectionReviewLinkRepo.save(any(PostCollectionReviewLinkEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountProfileService.getAllConnectedAccounts(eq("workspace_1"), anyBoolean()))
                .thenReturn(List.of(createConnectedAccount("account_x", Platform.x, "socialraven_x")));
        when(postCollaborationService.getClientVisibleThreads(1L, "workspace_1")).thenReturn(List.of());

        var response = service.getPublicReview("signed-token", null);

        assertThat(response.getOverallStatus()).isEqualTo("APPROVED");
        assertThat(response.isCanComment()).isFalse();
        assertThat(response.isCanApprove()).isFalse();
        assertThat(response.isCanReject()).isFalse();
    }

    private PostCollectionReviewLinkService createService(PostCollectionRepo postCollectionRepo,
                                                          PostCollectionReviewLinkRepo postCollectionReviewLinkRepo,
                                                          WorkspaceCapabilityService workspaceCapabilityService,
                                                          PostReviewLinkTokenService postReviewLinkTokenService,
                                                          ClerkUserService clerkUserService,
                                                          PostCollaborationService postCollaborationService,
                                                          PostService postService,
                                                          AccountProfileService accountProfileService,
                                                          StorageService storageService) {
        PostCollectionReviewLinkService service = new PostCollectionReviewLinkService();
        ReflectionTestUtils.setField(service, "postCollectionRepo", postCollectionRepo);
        ReflectionTestUtils.setField(service, "postCollectionReviewLinkRepo", postCollectionReviewLinkRepo);
        ReflectionTestUtils.setField(service, "workspaceCapabilityService", workspaceCapabilityService);
        ReflectionTestUtils.setField(service, "postReviewLinkTokenService", postReviewLinkTokenService);
        ReflectionTestUtils.setField(service, "clerkUserService", clerkUserService);
        ReflectionTestUtils.setField(service, "postCollaborationService", postCollaborationService);
        ReflectionTestUtils.setField(service, "postService", postService);
        ReflectionTestUtils.setField(service, "accountProfileService", accountProfileService);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        return service;
    }

    private PostCollectionEntity createCollection(String workspaceId, List<PostEntity> posts) {
        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setId(1L);
        collection.setWorkspaceId(workspaceId);
        collection.setCreatedBy("user_123");
        collection.setDescription("Campaign copy");
        collection.setDraft(true);
        collection.setReviewStatus(PostReviewStatus.DRAFT);
        collection.setPosts(posts);
        return collection;
    }

    private PostEntity createPost(Long id, Provider provider, String providerUserId) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setProvider(provider);
        post.setProviderUserId(providerUserId);
        post.setPostStatus(PostStatus.DRAFT);
        post.setPostType(PostType.TEXT);
        return post;
    }

    private ConnectedAccount createConnectedAccount(String providerUserId, Platform platform, String username) {
        ConnectedAccount account = new ConnectedAccount();
        account.setProviderUserId(providerUserId);
        account.setPlatform(platform);
        account.setUsername(username);
        return account;
    }

    private PostCollectionReviewLinkEntity createReviewLink(String id,
                                                            String workspaceId,
                                                            Long collectionId,
                                                            OffsetDateTime expiresAt) {
        PostCollectionReviewLinkEntity link = new PostCollectionReviewLinkEntity();
        link.setId(id);
        link.setWorkspaceId(workspaceId);
        link.setPostCollectionId(collectionId);
        link.setCreatedByUserId("user_123");
        link.setShareScope(PostReviewLinkShareScope.CAMPAIGN);
        link.setSharedPostIds("[]");
        link.setExpiresAt(expiresAt);
        link.setCreatedAt(OffsetDateTime.now().minusHours(1));
        return link;
    }
}
