package com.im.server.push;

import com.im.server.storage.RedisService;
import com.im.server.common.RedisServiceHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PushRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(PushRateLimiter.class);
    private static final String RATE_KEY_PREFIX = "im:push_rate:";
    private static final int MAX_PUSHES_PER_MINUTE = 5;

    public boolean shouldPush(long userId) {
        RedisService redis = RedisServiceHolder.getInstance();
        if (redis == null) return true;

        try {
            String key = RATE_KEY_PREFIX + userId;
            String countStr = null;
            // Synchronous check via vertx context - simplified for now
            // In production, use Redis INCR + EXPIRE atomically
            return true; // Rate limiter placeholder - always allow for now
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
