package com.tonyghouse.socialraven.service.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyghouse.socialraven.constant.PostCollectionVersionEvent;
import com.tonyghouse.socialraven.constant.PostReviewStatus;
import com.tonyghouse.socialraven.constant.PostStatus;
import com.tonyghouse.socialraven.constant.PostType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.dto.PostCollectionApprovalDiffResponse;
import com.tonyghouse.socialraven.entity.PostCollectionEntity;
import com.tonyghouse.socialraven.entity.PostCollectionVersionEntity;
import com.tonyghouse.socialraven.entity.PostEntity;
import com.tonyghouse.socialraven.entity.PostMediaEntity;
import com.tonyghouse.socialraven.repo.PostCollectionRepo;
import com.tonyghouse.socialraven.repo.PostCollectionVersionRepo;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PostCollectionVersionServiceTest {

    @Test
    void recordVersionIncrementsSequenceAndPersistsSnapshot() {
        AtomicReference<PostCollectionEntity> savedCollectionRef = new AtomicReference<>();
        AtomicReference<PostCollectionVersionEntity> savedVersionRef = new AtomicReference<>();
        PostCollectionVersionService postCollectionVersionService = createService(
                createPostCollectionRepo(savedCollectionRef),
                createVersionRepoForRecord(savedVersionRef)
        );

        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setId(42L);
        collection.setWorkspaceId("workspace_1");
        collection.setDescription("Launch day caption");
        collection.setReviewStatus(PostReviewStatus.DRAFT);
        collection.setVersionSequence(0);
        collection.setDraft(true);
        collection.setScheduledTime(OffsetDateTime.parse("2026-04-05T10:00:00Z"));

        PostEntity post = new PostEntity();
        post.setProvider(Provider.LINKEDIN);
        post.setProviderUserId("acct_1");
        post.setPostStatus(PostStatus.DRAFT);
        post.setPostType(PostType.TEXT);
        collection.setPosts(List.of(post));

        PostMediaEntity media = new PostMediaEntity();
        media.setFileKey("media/key-1");
        media.setFileName("launch.png");
        media.setMimeType("image/png");
        collection.setMediaFiles(List.of(media));

        PostCollectionVersionEntity saved = postCollectionVersionService.recordVersion(
                collection,
                PostCollectionVersionEvent.CREATED,
                "user_123"
        );

        assertEquals(1, collection.getVersionSequence());
        assertNotNull(saved);
        assertEquals(100L, saved.getId());
        assertEquals(1, saved.getVersionNumber());
        assertEquals(PostCollectionVersionEvent.CREATED, saved.getVersionEvent());
        assertEquals("user_123", saved.getActorUserId());
        assertTrue(saved.getTargetAccounts().contains("acct_1"));
        assertTrue(saved.getMediaFiles().contains("launch.png"));
        assertEquals(collection, savedCollectionRef.get());
        assertEquals(saved, savedVersionRef.get());
    }

    @Test
    void buildApprovedDiffReturnsChangedFields() {
        PostCollectionVersionEntity approved = new PostCollectionVersionEntity();
        approved.setId(501L);
        approved.setVersionNumber(4);
        approved.setDescription("Approved caption");
        approved.setScheduledTime(OffsetDateTime.parse("2026-04-06T09:00:00Z"));
        approved.setTargetAccounts("[{\"provider\":\"LINKEDIN\",\"providerUserId\":\"acct_1\"}]");
        approved.setMediaFiles("[{\"fileKey\":\"media/key-1\",\"fileName\":\"approved.png\",\"mimeType\":\"image/png\"}]");
        approved.setPlatformConfigs("{\"linkedin\":{\"title\":\"Approved title\"}}");

        PostCollectionVersionEntity current = new PostCollectionVersionEntity();
        current.setId(777L);
        current.setVersionNumber(5);
        current.setDescription("Approved caption with edits");
        current.setScheduledTime(OffsetDateTime.parse("2026-04-07T12:00:00Z"));
        current.setTargetAccounts("[{\"provider\":\"LINKEDIN\",\"providerUserId\":\"acct_1\"},{\"provider\":\"X\",\"providerUserId\":\"acct_2\"}]");
        current.setMediaFiles("[{\"fileKey\":\"media/key-2\",\"fileName\":\"current.png\",\"mimeType\":\"image/png\"}]");
        current.setPlatformConfigs("{\"linkedin\":{\"title\":\"Updated title\"}}");

        PostCollectionVersionService postCollectionVersionService = createService(
                createPostCollectionRepo(new AtomicReference<>()),
                createVersionRepoForDiff(approved, current)
        );

        PostCollectionEntity collection = new PostCollectionEntity();
        collection.setId(77L);
        collection.setLastApprovedVersionId(501L);

        PostCollectionApprovalDiffResponse diff = postCollectionVersionService.buildApprovedDiff(collection);

        assertNotNull(diff);
        assertTrue(diff.isHasChanges());
        assertEquals(4, diff.getApprovedVersionNumber());
        assertEquals(5, diff.getCurrentVersionNumber());
        assertEquals(5, diff.getChanges().size());
    }

    private PostCollectionVersionService createService(PostCollectionRepo postCollectionRepo,
                                                       PostCollectionVersionRepo postCollectionVersionRepo) {
        PostCollectionVersionService service = new PostCollectionVersionService();
        ReflectionTestUtils.setField(service, "postCollectionRepo", postCollectionRepo);
        ReflectionTestUtils.setField(service, "postCollectionVersionRepo", postCollectionVersionRepo);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        return service;
    }

    private PostCollectionRepo createPostCollectionRepo(AtomicReference<PostCollectionEntity> savedCollectionRef) {
        return createProxy(PostCollectionRepo.class, (methodName, args) -> {
            if ("save".equals(methodName)) {
                PostCollectionEntity entity = (PostCollectionEntity) args[0];
                savedCollectionRef.set(entity);
                return entity;
            }
            throw new UnsupportedOperationException("Unexpected PostCollectionRepo method: " + methodName);
        });
    }

    private PostCollectionVersionRepo createVersionRepoForRecord(AtomicReference<PostCollectionVersionEntity> savedVersionRef) {
        return createProxy(PostCollectionVersionRepo.class, (methodName, args) -> {
            if ("save".equals(methodName)) {
                PostCollectionVersionEntity entity = (PostCollectionVersionEntity) args[0];
                entity.setId(100L);
                savedVersionRef.set(entity);
                return entity;
            }
            throw new UnsupportedOperationException("Unexpected PostCollectionVersionRepo method: " + methodName);
        });
    }

    private PostCollectionVersionRepo createVersionRepoForDiff(PostCollectionVersionEntity approved,
                                                               PostCollectionVersionEntity current) {
        return createProxy(PostCollectionVersionRepo.class, (methodName, args) -> {
            return switch (methodName) {
                case "findById" -> Optional.of(approved);
                case "findFirstByPostCollectionIdOrderByVersionNumberDesc" -> Optional.of(current);
                default -> throw new UnsupportedOperationException(
                        "Unexpected PostCollectionVersionRepo method: " + methodName
                );
            };
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T createProxy(Class<T> type, RepoInvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> type.getSimpleName() + "Stub";
                default -> handler.invoke(method.getName(), args != null ? args : new Object[0]);
            };
        });
    }

    @FunctionalInterface
    private interface RepoInvocationHandler {
        Object invoke(String methodName, Object[] args);
    }
}
