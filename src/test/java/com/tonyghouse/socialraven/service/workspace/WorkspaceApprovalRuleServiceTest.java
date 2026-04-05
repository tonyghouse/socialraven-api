package com.tonyghouse.socialraven.service.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tonyghouse.socialraven.constant.PostCollectionType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.constant.WorkspaceApprovalMode;
import com.tonyghouse.socialraven.dto.ConnectedAccount;
import com.tonyghouse.socialraven.dto.workspace.WorkspaceApprovalRuleRequest;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.WorkspaceApprovalRuleRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import com.tonyghouse.socialraven.service.account_profile.AccountProfileService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WorkspaceApprovalRuleServiceTest {

    @Test
    void replaceRulesRejectsUnknownAccountScopeValue() {
        WorkspaceApprovalRuleRepo workspaceApprovalRuleRepo = mock(WorkspaceApprovalRuleRepo.class);
        WorkspaceRepo workspaceRepo = mock(WorkspaceRepo.class);
        AccountProfileService accountProfileService = mock(AccountProfileService.class);

        WorkspaceApprovalRuleService service = createService(
                workspaceApprovalRuleRepo,
                workspaceRepo,
                accountProfileService
        );

        when(workspaceRepo.findByIdAndDeletedAtIsNull("workspace_1"))
                .thenReturn(Optional.of(workspace("workspace_1", WorkspaceApprovalMode.OPTIONAL)));
        when(accountProfileService.getAllConnectedAccounts("workspace_1", false)).thenReturn(List.of());

        WorkspaceApprovalRuleRequest request = new WorkspaceApprovalRuleRequest();
        request.setScopeType("ACCOUNT");
        request.setScopeValue("acct_missing");
        request.setApprovalMode("REQUIRED");

        assertThatThrownBy(() -> service.replaceRules("workspace_1", List.of(request)))
                .isInstanceOf(SocialRavenException.class)
                .hasMessage("Account approval rules must target a currently connected account");
    }

    @Test
    void resolveApprovalModeUsesOverrideThenStrictestAccountThenContentTypeThenWorkspaceDefault() {
        WorkspaceApprovalRuleService service = createService(
                mock(WorkspaceApprovalRuleRepo.class),
                mock(WorkspaceRepo.class),
                mock(AccountProfileService.class)
        );

        Map<String, WorkspaceApprovalMode> accountModes = Map.of(
                "acct_1", WorkspaceApprovalMode.OPTIONAL,
                "acct_2", WorkspaceApprovalMode.REQUIRED
        );
        EnumMap<PostCollectionType, WorkspaceApprovalMode> contentTypeModes =
                new EnumMap<>(PostCollectionType.class);
        contentTypeModes.put(PostCollectionType.TEXT, WorkspaceApprovalMode.NONE);

        WorkspaceApprovalRuleService.ApprovalRuleSnapshot snapshot =
                new WorkspaceApprovalRuleService.ApprovalRuleSnapshot(
                        WorkspaceApprovalMode.MULTI_STEP,
                        true,
                        accountModes,
                        contentTypeModes
                );

        PostCollectionEntity accountScopedCollection = collection(
                PostCollectionType.TEXT,
                null,
                List.of(post("acct_1"), post("acct_2"))
        );
        assertThat(service.resolveApprovalMode(snapshot, accountScopedCollection))
                .isEqualTo(WorkspaceApprovalMode.REQUIRED);

        PostCollectionEntity contentTypeScopedCollection = collection(
                PostCollectionType.TEXT,
                null,
                List.of(post("acct_unscoped"))
        );
        assertThat(service.resolveApprovalMode(snapshot, contentTypeScopedCollection))
                .isEqualTo(WorkspaceApprovalMode.NONE);

        PostCollectionEntity overrideCollection = collection(
                PostCollectionType.TEXT,
                WorkspaceApprovalMode.OPTIONAL,
                List.of(post("acct_2"))
        );
        assertThat(service.resolveApprovalMode(snapshot, overrideCollection))
                .isEqualTo(WorkspaceApprovalMode.OPTIONAL);

        PostCollectionEntity workspaceDefaultCollection = collection(
                PostCollectionType.IMAGE,
                null,
                List.of(post("acct_unscoped"))
        );
        assertThat(service.resolveApprovalMode(snapshot, workspaceDefaultCollection))
                .isEqualTo(WorkspaceApprovalMode.MULTI_STEP);
    }

    private WorkspaceApprovalRuleService createService(WorkspaceApprovalRuleRepo workspaceApprovalRuleRepo,
                                                       WorkspaceRepo workspaceRepo,
                                                       AccountProfileService accountProfileService) {
        WorkspaceApprovalRuleService service = new WorkspaceApprovalRuleService();
        ReflectionTestUtils.setField(service, "workspaceApprovalRuleRepo", workspaceApprovalRuleRepo);
        ReflectionTestUtils.setField(service, "workspaceRepo", workspaceRepo);
        ReflectionTestUtils.setField(service, "accountProfileService", accountProfileService);
        return service;
    }

    private WorkspaceEntity workspace(String id, WorkspaceApprovalMode approvalMode) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId(id);
        workspace.setApprovalMode(approvalMode);
        workspace.setAutoScheduleAfterApproval(true);
        return workspace;
    }

    private PostCollectionEntity collection(PostCollectionType type,
                                            WorkspaceApprovalMode approvalModeOverride,
                                            List<PostEntity> posts) {
        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setPostCollectionType(type);
        collection.setApprovalModeOverride(approvalModeOverride);
        collection.setPosts(posts);
        return collection;
    }

    private PostEntity post(String providerUserId) {
        PostEntity post = new PostEntity();
        post.setProviderUserId(providerUserId);
        post.setProvider(Provider.LINKEDIN);
        return post;
    }
}
