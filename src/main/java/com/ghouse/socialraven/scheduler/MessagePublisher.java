package com.ghouse.socialraven.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String POST_QUEUE = "post:queue";
    private static final String OAUTH_REFRESH_QUEUE = "oauth_refresh:queue";

    /**
     * Push post IDs batch to FIFO queue
     */
    public void publishPostIds(List<Long> postIdsBatch) {
        if (postIdsBatch == null || postIdsBatch.isEmpty()) return;

        try (Jedis jedis = jedisPool.getResource()) {

            Pipeline pipeline = jedis.pipelined();

            for (Long id : postIdsBatch) {
                pipeline.lpush(POST_QUEUE, id.toString());
            }

            pipeline.sync();

            log.info("Published {} post jobs to queue", postIdsBatch.size());

        } catch (Exception e) {
            log.error("Failed publishing post batch", e);
        }
    }

    /**
     * Push OAuth refresh IDs batch to queue
     */
    public void refreshOAuthInfoIds(List<Long> oauthInfoIdsBatch) {
        if (oauthInfoIdsBatch == null || oauthInfoIdsBatch.isEmpty()) return;

        try (Jedis jedis = jedisPool.getResource()) {

            Pipeline pipeline = jedis.pipelined();

            for (Long id : oauthInfoIdsBatch) {
                pipeline.lpush(OAUTH_REFRESH_QUEUE, id.toString());
            }

            pipeline.sync();

            log.info("Published {} oauth refresh jobs", oauthInfoIdsBatch.size());

        } catch (Exception e) {
            log.error("Failed publishing oauth refresh batch", e);
        }
    }
}
