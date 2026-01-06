package com.ghouse.socialraven.scheduler;

import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class PostRedisService {

    private final JedisPool jedisPool;

    public PostRedisService(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * Fetch posts with score <= maxScore
     */
    public List<Long> fetchIds(String key, long maxScore) {

        try (Jedis jedis = jedisPool.getResource()) {

            List<String> values =
                    jedis.zrangeByScore(key, Double.NEGATIVE_INFINITY, maxScore);

            List<Long> result = new ArrayList<>();

            if (values != null) {
                for (String value : values) {
                    result.add(Long.parseLong(value));
                }
            }

            return result;
        }
    }

    /**
     * Remove posts after pickup (prevent requeue)
     */
    public void removePosts(String key, List<Long> postIds) {

        if (postIds == null || postIds.isEmpty()) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {

            String[] members = postIds
                    .stream()
                    .map(String::valueOf)
                    .toArray(String[]::new);

            jedis.zrem(key, members);
        }
    }
}
