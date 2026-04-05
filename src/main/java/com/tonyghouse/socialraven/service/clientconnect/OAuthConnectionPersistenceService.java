package com.tonyghouse.socialraven.service.clientconnect;

import com.tonyghouse.socialraven.constant.OAuthConnectionOwnerType;
import com.tonyghouse.socialraven.constant.Provider;
import com.tonyghouse.socialraven.entity.OAuthInfoEntity;
import com.tonyghouse.socialraven.helper.RedisTokenExpirySaver;
import com.tonyghouse.socialraven.model.AdditionalOAuthInfo;
import com.tonyghouse.socialraven.repo.OAuthInfoRepo;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OAuthConnectionPersistenceService {

    @Autowired
    private OAuthInfoRepo oauthInfoRepo;

    @Autowired
    private RedisTokenExpirySaver redisTokenExpirySaver;

    public PersistedConnection saveWorkspaceMemberConnection(String workspaceId,
                                                             String userId,
                                                             Provider provider,
                                                             String providerUserId,
                                                             String accessToken,
                                                             OffsetDateTime expiresAtUtc,
                                                             AdditionalOAuthInfo additionalInfo) {
        OAuthInfoEntity existing = oauthInfoRepo.findByWorkspaceIdAndProviderAndProviderUserId(
                workspaceId,
                provider,
                providerUserId
        );
        return saveConnection(
                existing,
                workspaceId,
                userId,
                provider,
                providerUserId,
                accessToken,
                expiresAtUtc,
                additionalInfo,
                existing != null && existing.getConnectionOwnerType() == OAuthConnectionOwnerType.CLIENT_HANDOFF
                        ? OAuthConnectionOwnerType.CLIENT_HANDOFF
                        : OAuthConnectionOwnerType.WORKSPACE_MEMBER,
                existing != null && existing.getConnectionOwnerType() == OAuthConnectionOwnerType.CLIENT_HANDOFF
                        ? existing.getConnectionOwnerDisplayName()
                        : null,
                existing != null && existing.getConnectionOwnerType() == OAuthConnectionOwnerType.CLIENT_HANDOFF
                        ? existing.getConnectionOwnerEmail()
                        : null,
                existing != null && existing.getConnectionOwnerType() == OAuthConnectionOwnerType.CLIENT_HANDOFF
                        ? existing.getClientConnectionSessionId()
                        : null
        );
    }

    public PersistedConnection saveClientConnection(String workspaceId,
                                                    String managingUserId,
                                                    String sessionId,
                                                    String ownerDisplayName,
                                                    String ownerEmail,
                                                    Provider provider,
                                                    String providerUserId,
                                                    String accessToken,
                                                    OffsetDateTime expiresAtUtc,
                                                    AdditionalOAuthInfo additionalInfo) {
        OAuthInfoEntity existing = oauthInfoRepo.findByWorkspaceIdAndProviderAndProviderUserId(
                workspaceId,
                provider,
                providerUserId
        );
        return saveConnection(
                existing,
                workspaceId,
                managingUserId,
                provider,
                providerUserId,
                accessToken,
                expiresAtUtc,
                additionalInfo,
                OAuthConnectionOwnerType.CLIENT_HANDOFF,
                ownerDisplayName,
                ownerEmail,
                sessionId
        );
    }

    private PersistedConnection saveConnection(OAuthInfoEntity existing,
                                               String workspaceId,
                                               String userId,
                                               Provider provider,
                                               String providerUserId,
                                               String accessToken,
                                               OffsetDateTime expiresAtUtc,
                                               AdditionalOAuthInfo additionalInfo,
                                               OAuthConnectionOwnerType ownerType,
                                               String ownerDisplayName,
                                               String ownerEmail,
                                               String sessionId) {
        boolean reauthorized = existing != null;
        OAuthInfoEntity entity = reauthorized ? existing : new OAuthInfoEntity();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        entity.setWorkspaceId(workspaceId);
        entity.setUserId(userId);
        entity.setProvider(provider);
        entity.setProviderUserId(providerUserId);
        entity.setAccessToken(accessToken);
        entity.setExpiresAt(expiresAtUtc.toInstant().toEpochMilli());
        entity.setExpiresAtUtc(expiresAtUtc);
        entity.setAdditionalInfo(additionalInfo != null ? additionalInfo : new AdditionalOAuthInfo());
        entity.setConnectionOwnerType(ownerType);
        entity.setConnectionOwnerDisplayName(ownerDisplayName);
        entity.setConnectionOwnerEmail(ownerEmail);
        entity.setClientConnectionSessionId(sessionId);
        if (!reauthorized || entity.getConnectedAt() == null) {
            entity.setConnectedAt(now);
        }
        entity.setLastReauthorizedAt(reauthorized ? now : null);

        OAuthInfoEntity saved = oauthInfoRepo.save(entity);
        redisTokenExpirySaver.saveTokenExpiry(saved);
        return new PersistedConnection(saved, reauthorized);
    }

    public record PersistedConnection(OAuthInfoEntity entity, boolean reauthorized) {
    }
}
