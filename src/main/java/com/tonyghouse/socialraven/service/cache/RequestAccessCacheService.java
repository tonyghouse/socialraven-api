package com.tonyghouse.socialraven.service.cache;

import com.tonyghouse.socialraven.constant.UserStatus;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.Optional;

@Service
@Slf4j
public class RequestAccessCacheService {

    private final JedisPool jedisPool;

    @Value("${socialraven.cache.workspace-role.ttl-seconds:300}")
    private int workspaceRoleTtlSeconds;

    @Value("${socialraven.cache.user-status.ttl-seconds:60}")
    private int userStatusTtlSeconds;

    @Value("${socialraven.cache.redis-op.max-attempts:3}")
    private int redisOpMaxAttempts;

    @Value("${socialraven.cache.redis-op.initial-backoff-ms:20}")
    private long redisOpInitialBackoffMs;

    @Value("${socialraven.cache.redis-op.max-backoff-ms:100}")
    private long redisOpMaxBackoffMs;

    public RequestAccessCacheService(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public Optional<WorkspaceRole> getWorkspaceRole(String workspaceId, String userId) {
        String key = workspaceRoleKey(workspaceId, userId);
        try (Jedis jedis = jedisPool.getResource()) {
            String role = jedis.get(key);
            if (role == null || role.isBlank()) {
                return Optional.empty();
            }

            try {
                return Optional.of(WorkspaceRole.valueOf(role));
            } catch (IllegalArgumentException ex) {
                jedis.del(key);
                log.debug("Removed invalid cached workspace role entry for key={}", key);
                return Optional.empty();
            }
        } catch (Exception ex) {
            log.debug("Workspace role cache read failed for workspaceId={}, userId={}", workspaceId, userId, ex);
            return Optional.empty();
        }
    }

    public void cacheWorkspaceRole(String workspaceId, String userId, WorkspaceRole role) {
        runAfterCommitOrNow(() -> writeWorkspaceRole(workspaceId, userId, role));
    }

    public void evictWorkspaceRole(String workspaceId, String userId) {
        runAfterCommitOrNow(() -> deleteWorkspaceRole(workspaceId, userId));
    }

    public void evictWorkspaceRolesForWorkspace(String workspaceId) {
        runAfterCommitOrNow(() -> deleteWorkspaceRolesForWorkspace(workspaceId));
    }

    public Optional<UserStatus> getUserStatus(String userId) {
        String key = userStatusKey(userId);
        try (Jedis jedis = jedisPool.getResource()) {
            String status = jedis.get(key);
            if (status == null || status.isBlank()) {
                return Optional.empty();
            }

            try {
                return Optional.of(UserStatus.valueOf(status));
            } catch (IllegalArgumentException ex) {
                jedis.del(key);
                log.debug("Removed invalid cached user status entry for key={}", key);
                return Optional.empty();
            }
        } catch (Exception ex) {
            log.debug("User status cache read failed for userId={}", userId, ex);
            return Optional.empty();
        }
    }

    public void cacheUserStatus(String userId, UserStatus status) {
        runAfterCommitOrNow(() -> writeUserStatus(userId, status));
    }

    public void evictUserStatus(String userId) {
        runAfterCommitOrNow(() -> deleteUserStatus(userId));
    }

    private String workspaceRoleKey(String workspaceId, String userId) {
        return "cache:workspace-role:v1:" + workspaceId + ":" + userId;
    }

    private String userStatusKey(String userId) {
        return "cache:user-status:v1:" + userId;
    }

    private void runAfterCommitOrNow(Runnable operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    operation.run();
                }
            });
            return;
        }
        operation.run();
    }

    private void writeWorkspaceRole(String workspaceId, String userId, WorkspaceRole role) {
        String key = workspaceRoleKey(workspaceId, userId);
        executeWithRetry(
                "cacheWorkspaceRole",
                "workspaceId=" + workspaceId + ", userId=" + userId,
                () -> {
                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.setex(key, workspaceRoleTtlSeconds, role.name());
                    }
                }
        );
    }

    private void deleteWorkspaceRole(String workspaceId, String userId) {
        String key = workspaceRoleKey(workspaceId, userId);
        executeWithRetry(
                "evictWorkspaceRole",
                "workspaceId=" + workspaceId + ", userId=" + userId,
                () -> {
                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.del(key);
                    }
                }
        );
    }

    private void deleteWorkspaceRolesForWorkspace(String workspaceId) {
        executeWithRetry(
                "evictWorkspaceRolesForWorkspace",
                "workspaceId=" + workspaceId,
                () -> {
                    String pattern = "cache:workspace-role:v1:" + workspaceId + ":*";
                    try (Jedis jedis = jedisPool.getResource()) {
                        String cursor = ScanParams.SCAN_POINTER_START;
                        ScanParams params = new ScanParams().match(pattern).count(100);

                        do {
                            ScanResult<String> scanResult = jedis.scan(cursor, params);
                            cursor = scanResult.getCursor();
                            if (!scanResult.getResult().isEmpty()) {
                                jedis.del(scanResult.getResult().toArray(String[]::new));
                            }
                        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
                    }
                }
        );
    }

    private void writeUserStatus(String userId, UserStatus status) {
        String key = userStatusKey(userId);
        executeWithRetry(
                "cacheUserStatus",
                "userId=" + userId,
                () -> {
                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.setex(key, userStatusTtlSeconds, status.name());
                    }
                }
        );
    }

    private void deleteUserStatus(String userId) {
        String key = userStatusKey(userId);
        executeWithRetry(
                "evictUserStatus",
                "userId=" + userId,
                () -> {
                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.del(key);
                    }
                }
        );
    }

    private void executeWithRetry(String operationName, String context, Runnable redisOperation) {
        int maxAttempts = Math.max(redisOpMaxAttempts, 1);
        long maxBackoff = Math.max(redisOpMaxBackoffMs, redisOpInitialBackoffMs);
        long backoff = Math.max(redisOpInitialBackoffMs, 0L);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                redisOperation.run();
                return;
            } catch (Exception ex) {
                if (attempt == maxAttempts) {
                    log.warn("Redis {} failed after {} attempts ({})", operationName, maxAttempts, context, ex);
                    return;
                }

                if (backoff > 0) {
                    sleepQuietly(backoff);
                }
                backoff = Math.min(Math.max(backoff * 2, 1L), maxBackoff);
            }
        }
    }

    private void sleepQuietly(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
