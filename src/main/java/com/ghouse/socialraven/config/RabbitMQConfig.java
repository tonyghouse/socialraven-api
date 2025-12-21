package com.ghouse.socialraven.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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

    /**
     * Queue declaration
     */
    @Bean
    public Queue postPublishQueue() {
        return new Queue(POST_PUBLISH_QUEUE, true); // durable
    }

    /**
     * RabbitMQ connection
     */
    @Bean
    public ConnectionFactory rabbitConnectionFactory() {
        CachingConnectionFactory factory =
                new CachingConnectionFactory(host, Integer.parseInt(port));
        factory.setUsername(username);
        factory.setPassword(password);

        // Optional but recommended
        factory.setChannelCacheSize(25);

        return factory;
    }

    /**
     * Thread pool dedicated ONLY for RabbitMQ consumers
     */
    @Bean
    public ThreadPoolTaskExecutor rabbitListenerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);      // baseline concurrency
        executor.setMaxPoolSize(10);      // peak concurrency
        executor.setQueueCapacity(100);   // buffering
        executor.setThreadNamePrefix("rabbit-consumer-");
        executor.initialize();
        return executor;
    }

    /**
     * Listener container with concurrency + fairness
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory rabbitConnectionFactory,
            ThreadPoolTaskExecutor rabbitListenerTaskExecutor
    ) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(rabbitConnectionFactory);
        factory.setTaskExecutor(rabbitListenerTaskExecutor);

        // 🔥 THIS is where concurrency actually comes from
        factory.setConcurrentConsumers(5);
        factory.setMaxConcurrentConsumers(10);

        // Fair dispatch: one unacked message per consumer
        factory.setPrefetchCount(1);

        // Do not requeue endlessly on failure (DLQ-friendly)
        factory.setDefaultRequeueRejected(false);

        return factory;
    }
}
