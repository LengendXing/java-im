package com.im.server.storage;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RedisService {
    private static final Logger log = LoggerFactory.getLogger(RedisService.class);
    private static final String KEY_PREFIX = "im";
    private static final String ROUTE_KEY = KEY_PREFIX + ":route:";
    private static final String SEQ_KEY = KEY_PREFIX + ":seq:";
    private static final String UNREAD_KEY = KEY_PREFIX + ":unread:";
    private static final String TOKEN_KEY = KEY_PREFIX + ":token:";
    private static final String GROUP_MEMBERS_KEY = KEY_PREFIX + ":group_members:";

    private RedisAPI redis;

    public Future<Void> init(Vertx vertx, String host, int port, String password, int database) {
        Promise<Void> promise = Promise.promise();
        String connectionString;
        if (password != null && !password.isEmpty()) {
            connectionString = "redis://:" + password + "@" + host + ":" + port + "/" + database;
        } else {
            connectionString = "redis://" + host + ":" + port + "/" + database;
        }

        Redis.createClient(vertx, new RedisOptions().setConnectionString(connectionString))
                .connect()
                .onSuccess(conn -> {
                    this.redis = RedisAPI.api(conn);
                    log.info("Redis connected: {}:{}", host, port);
                    promise.complete();
                })
                .onFailure(err -> {
                    log.error("Redis connect failed: {}", err.getMessage());
                    promise.fail(err);
                });

        return promise.future();
    }

    public Future<Void> setUserOnline(long userId, String gatewayId, long ttlSeconds) {
        return redis.setex(ROUTE_KEY + userId, String.valueOf(ttlSeconds), gatewayId)
                .mapEmpty();
    }

    public Future<Void> setUserOffline(long userId) {
        return redis.del(List.of(ROUTE_KEY + userId)).mapEmpty();
    }

    public Future<String> getRoute(long userId) {
        Promise<String> promise = Promise.promise();
        redis.get(ROUTE_KEY + userId)
                .onSuccess(val -> promise.complete(val == null ? null : val.toString()))
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Long> incrSeq(String sessionId) {
        Promise<Long> promise = Promise.promise();
        redis.incr(SEQ_KEY + sessionId)
                .onSuccess(val -> promise.complete(Long.parseLong(val.toString())))
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Long> incrUnread(long userId, String sessionId) {
        Promise<Long> promise = Promise.promise();
        redis.incr(UNREAD_KEY + userId + ":" + sessionId)
                .onSuccess(val -> promise.complete(Long.parseLong(val.toString())))
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Long> getUnread(long userId, String sessionId) {
        Promise<Long> promise = Promise.promise();
        redis.get(UNREAD_KEY + userId + ":" + sessionId)
                .onSuccess(val -> promise.complete(val == null ? 0L : Long.parseLong(val.toString())))
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> resetUnread(long userId, String sessionId) {
        return redis.del(List.of(UNREAD_KEY + userId + ":" + sessionId)).mapEmpty();
    }

    public Future<Void> storeToken(long userId, String token, long ttlSeconds) {
        return redis.setex(TOKEN_KEY + token, String.valueOf(ttlSeconds), String.valueOf(userId))
                .mapEmpty();
    }

    public Future<Long> validateToken(String token) {
        Promise<Long> promise = Promise.promise();
        redis.get(TOKEN_KEY + token)
                .onSuccess(val -> promise.complete(val == null ? -1L : Long.parseLong(val.toString())))
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> deleteToken(String token) {
        return redis.del(List.of(TOKEN_KEY + token)).mapEmpty();
    }

    public Future<Void> cacheGroupMembers(long groupId, String memberIdsCsv, long ttlSeconds) {
        return redis.setex(GROUP_MEMBERS_KEY + groupId, String.valueOf(ttlSeconds), memberIdsCsv)
                .mapEmpty();
    }

    public Future<String> getCachedGroupMembers(long groupId) {
        Promise<String> promise = Promise.promise();
        redis.get(GROUP_MEMBERS_KEY + groupId)
                .onSuccess(val -> promise.complete(val == null ? null : val.toString()))
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> invalidateGroupMembersCache(long groupId) {
        return redis.del(List.of(GROUP_MEMBERS_KEY + groupId)).mapEmpty();
    }

    public RedisAPI getRedisAPI() {
        return redis;
    }
}
