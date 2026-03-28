package com.tonyghouse.socialraven.scheduler;

import com.tonyghouse.socialraven.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessagePublisher {

    private final JedisPool jedisPool;
    private final RabbitTemplate rabbitTemplate;

    private static final String ANALYTICS_QUEUE = "analytics:queue";

    /**
     * Push post IDs to RabbitMQ post-publish-queue
     */
    public void publishPostIds(List<Long> postIdsBatch) {
        if (postIdsBatch == null || postIdsBatch.isEmpty()) return;

        for (Long id : postIdsBatch) {
            rabbitTemplate.convertAndSend(RabbitMQConfig.POST_PUBLISH_QUEUE, id.toString());
        }

        log.info("Published {} post jobs to RabbitMQ", postIdsBatch.size());
    }

    /**
     * Push analytics job IDs batch to Redis analytics queue
     */
    public void publishAnalyticsJobIds(List<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) return;

        try (Jedis jedis = jedisPool.getResource()) {

            Pipeline pipeline = jedis.pipelined();

            for (Long id : jobIds) {
                pipeline.lpush(ANALYTICS_QUEUE, id.toString());
            }

            pipeline.sync();

            log.info("Published {} analytics jobs to queue", jobIds.size());

        } catch (Exception e) {
            log.error("Failed publishing analytics job batch", e);
        }
    }

    /**
     * Push OAuth refresh IDs to RabbitMQ oauth-refresh-queue
     */
    public void refreshOAuthInfoIds(List<Long> oauthInfoIdsBatch) {
        if (oauthInfoIdsBatch == null || oauthInfoIdsBatch.isEmpty()) return;

        for (Long id : oauthInfoIdsBatch) {
            rabbitTemplate.convertAndSend(RabbitMQConfig.OAUTH_REFRESH_QUEUE, id.toString());
        }

        log.info("Published {} oauth refresh jobs to RabbitMQ", oauthInfoIdsBatch.size());
    }
}
