package com.ghouse.socialraven.helper;


import com.ghouse.socialraven.entity.OAuthInfoEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;


@Component
@Slf4j
public class RedisTokenExpirySaver {

    @Autowired
    private JedisPool jedisPool;

    private static final String OAUTH_EXPIRY_POOL_NAME ="oauth-expiry-pool";

    public void saveTokenExpiry(OAuthInfoEntity oauthInfo) {
        long expiresAt = oauthInfo.getExpiresAt();

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd(OAUTH_EXPIRY_POOL_NAME, expiresAt, oauthInfo.getId().toString());
            log.info("OAuthInfo Token expiry Added to Redis pool: oauthInfoId={}, expiresUTC={}", oauthInfo.getId(), expiresAt);
        }
    }
}
