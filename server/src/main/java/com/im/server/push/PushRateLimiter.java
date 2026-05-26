package com.im.server.push;

import com.im.server.storage.RedisService;
import com.im.server.common.RedisServiceHolder;
import io.vertx.redis.client.RedisAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PushRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(PushRateLimiter.class);
    private static final String RATE_KEY_PREFIX = "im:push_rate:";
    private static final int MAX_PUSHES_PER_MINUTE = 5;
    private static final int WINDOW_SECONDS = 60;

    public boolean shouldPush(long userId) {
        RedisService redis = RedisServiceHolder.getInstance();
        if (redis == null) return true;
        try {
            RedisAPI api = redis.getRedisAPI();
            String key = RATE_KEY_PREFIX + userId;
            long now = System.currentTimeMillis();
            String member = String.valueOf(now);
            api.zadd(List.of(key, String.valueOf(now), member))
                    .compose(v -> api.zremrangebyscore(key, "0", String.valueOf(now - WINDOW_SECONDS * 1000)))
                    .compose(v -> api.zcard(key)
                            .onSuccess(count -> {
                                if (count != null && Long.parseLong(count.toString()) > MAX_PUSHES_PER_MINUTE) {
                                    log.debug("rate limited userId={}, count={}", userId, count);
                                }
                            }))
                    .compose(v -> api.expire(List.of(key, String.valueOf(WINDOW_SECONDS + 10))));
            return true;
        } catch (Exception e) {
            log.warn("Rate limiter error for userId={}: {}", userId, e.getMessage());
            return true;
        }
    }

    public String getMergedMessage(int count) {
        if (count <= 1) return null;
        return "你有 " + count + " 条新消息";
    }
}
