package com.ghouse.socialraven.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
public class RabbitPublisher {

    private final ConnectionFactory connectionFactory;

    public RabbitPublisher(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void publishPostIds(List<Long> postIdsBatch) {

        try (Connection connection = connectionFactory.createConnection();
             com.rabbitmq.client.Channel channel =
                     connection.createChannel(false)) {

            String exchange = "post.publish.exchange";
            String routingKey = "post.publish";

            if (isProd()) {
                String prodQueue = "post-publish-queue";
                channel.queueBind(prodQueue, exchange, routingKey);
            } else {
                String localQueue = "post-publish-queue-local";
                channel.queueDeclare(localQueue, true, false, false, null);
                channel.queueBind(localQueue, exchange, routingKey);
            }

            // Enable publisher confirms
            channel.confirmSelect();

            int maxMessages = Math.min(postIdsBatch.size(), 1000);

            for (int i = 0; i < maxMessages; i++) {
                Long postId = postIdsBatch.get(i);
                channel.basicPublish(
                        exchange,
                        routingKey,
                        null,
                        postId.toString().getBytes(StandardCharsets.UTF_8)
                );
            }

            // Bulk confirm
            channel.waitForConfirmsOrDie(5000);

            log.info(
                    "Bulk messages published and confirmed. Total count: " + maxMessages
            );

        } catch (Exception e) {
            log.error("Failed to publish bulk messages: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void refreshOAuthInfo(Long oauthInfoId) {

        try (Connection connection = connectionFactory.createConnection();
             com.rabbitmq.client.Channel channel =
                     connection.createChannel(false)) {

            String exchange = "oauth.refresh.exchange";
            String routingKey = "oauth.refresh";

            channel.basicPublish(
                    exchange,
                    routingKey,
                    null,
                    oauthInfoId.toString().getBytes(StandardCharsets.UTF_8)
            );

            log.info("Message published OAuthInfoId: {} ", oauthInfoId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void refreshOAuthInfoIds(List<Long> oauthInfoIdsBatch) {

        try (Connection connection = connectionFactory.createConnection();
             com.rabbitmq.client.Channel channel =
                     connection.createChannel(false)) {

            String exchange = "oauth.refresh.exchange";
            String routingKey = "oauth.refresh";

            channel.confirmSelect();

            int maxMessages = Math.min(oauthInfoIdsBatch.size(), 1000);

            for (int i = 0; i < maxMessages; i++) {
                Long oauthInfoId = oauthInfoIdsBatch.get(i);
                channel.basicPublish(
                        exchange,
                        routingKey,
                        null,
                        oauthInfoId.toString().getBytes(StandardCharsets.UTF_8)
                );
            }

            channel.waitForConfirmsOrDie(5000);

            log.info(
                    "Bulk OAuth refresh messages published and confirmed. Total count: {}", maxMessages
            );

        } catch (Exception e) {
            log.error("Failed to publish bulk OAuth refresh messages: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isProd() {
        return false; // wire from Spring profile later
    }
}
