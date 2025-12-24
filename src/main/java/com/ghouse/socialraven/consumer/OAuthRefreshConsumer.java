package com.ghouse.socialraven.consumer;

import com.ghouse.socialraven.entity.OAuthInfoEntity;
import com.ghouse.socialraven.service.post.PostPublisherService;
import com.ghouse.socialraven.service.provider.OAuthInfoRefreshService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OAuthRefreshConsumer {

    @Autowired
    private OAuthInfoRefreshService oAuthInfoRefreshService;

    /**
     * Consumes messages from RabbitMQ queue: oauth-refresh-queue
     */
//    @RabbitListener(
//            queues = "oauth-refresh-queue",
//            containerFactory = "rabbitListenerContainerFactory"
//    )
    public void refreshOAuths(String oauthInfoId) {
        try {
            Long id = Long.valueOf(oauthInfoId);
            log.info("Received oauth refresh oauthInfoId={}", oauthInfoId);

            oAuthInfoRefreshService.refreshOAuthInfo(id);

        } catch (Exception e) {
            log.error("Failed to refresh oauthInfoId: {}", oauthInfoId, e);
            sendFailureEmail(oauthInfoId);
        }
    }

    private void sendFailureEmail(String oauthInfoId) {
        log.warn("Sending failure email for OAuthInfo id {}", oauthInfoId);
    }
}
