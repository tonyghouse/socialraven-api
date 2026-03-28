package com.tonyghouse.socialraven.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String POST_PUBLISH_QUEUE = "post-publish-queue";
    public static final String OAUTH_REFRESH_QUEUE = "oauth-refresh-queue";

    @Bean
    public Queue postPublishQueue() {
        return QueueBuilder.durable(POST_PUBLISH_QUEUE).build();
    }

    @Bean
    public Queue oauthRefreshQueue() {
        return QueueBuilder.durable(OAUTH_REFRESH_QUEUE).build();
    }
}
