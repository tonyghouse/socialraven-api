package com.ghouse.socialraven.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq-host}")
    private String host;

    @Value("${rabbitmq-port}")
    private String port;

    @Value("${rabbitmq-username}")
    private String username;

    @Value("${rabbitmq-password}")
    private String password;

    public static final String POST_PUBLISH_QUEUE = "post-publish-queue";

    @Bean
    public Queue postPublishQueue() {
        return new Queue(POST_PUBLISH_QUEUE, true); // durable
    }

    @Bean
    public ConnectionFactory rabbitConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory(host, Integer.parseInt(port));
        factory.setUsername(username);
        factory.setPassword(password);
        return factory;
    }
}
