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
        if (redisUrl != null && !redisUrl.isEmpty()) {

            // Format: redis://default:password@host:port
            URI uri = URI.create(redisUrl);

            String host = uri.getHost();
            int port = uri.getPort();
            String password = uri.getUserInfo().split(":", 2)[1];
            String username = uri.getUserInfo().split(":", 2)[0];

            return new JedisPool(
                    new JedisPoolConfig(),
                    host,
                    port,
                    2000,
                    username,
                    password,
                    false
            );
        }

        throw new IllegalStateException("REDIS_URL not set");
    }
}
