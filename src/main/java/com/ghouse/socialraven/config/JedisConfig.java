package com.ghouse.socialraven.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.net.URI;

@Configuration
public class JedisConfig {

    @Value("${redis.public.url}")
    private String redisUrl;

    @Bean
    public JedisPool jedisPool() {

        if (redisUrl == null || redisUrl.isEmpty()) {
            throw new IllegalStateException("redis.public.url not set");
        }

        URI uri = URI.create(redisUrl);

        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 6379 : uri.getPort();

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);

        if (uri.getUserInfo() != null) {
            String[] userInfoParts = uri.getUserInfo().split(":", 2);
            String username = userInfoParts.length > 1 ? userInfoParts[0] : null;
            String password = userInfoParts.length > 1 ? userInfoParts[1] : userInfoParts[0];

            return new JedisPool(
                    poolConfig,
                    host,
                    port,
                    2000,
                    username,
                    password,
                    false
            );
        }

        // No authentication
        return new JedisPool(
                poolConfig,
                host,
                port
        );
    }
}
