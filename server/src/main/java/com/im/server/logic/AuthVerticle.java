package com.im.server.logic;

import com.im.protocol.ImProto;
import com.im.server.common.Cmd;
import com.im.server.common.RequestEnvelope;
import com.im.server.common.DatabaseServiceHolder;
import com.im.server.common.RedisServiceHolder;
import com.im.server.common.ServerConfig;
import com.im.server.storage.DatabaseService;
import com.im.server.storage.RedisService;
import com.im.server.storage.User;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class AuthVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(AuthVerticle.class);

    private RedisService redisService;
    private DatabaseService dbService;
    private ServerConfig serverConfig;
    private SecretKey jwtKey;

    @Override
    public void start() {
        JsonObject cfg = config();
        serverConfig = ServerConfig.fromJson(cfg);

        redisService = RedisServiceHolder.getInstance();
        dbService = DatabaseServiceHolder.getInstance();

        jwtKey = Keys.hmacShaKeyFor(serverConfig.getJwtSecret().getBytes(StandardCharsets.UTF_8));

        // Handle long-connection AUTH (from Gateway)
        vertx.eventBus().consumer("im.logic.AUTH", msg -> {
            byte[] body = (byte[]) msg.body();
            handleLongConnAuth(msg, body);
        });

        // Handle HTTP register
        vertx.eventBus().consumer("im.logic.REGISTER", msg -> {
            JsonObject req = (JsonObject) msg.body();
            handleRegister(msg, req);
        });

        // Handle HTTP login
        vertx.eventBus().consumer("im.logic.LOGIN", msg -> {
            JsonObject req = (JsonObject) msg.body();
            handleLogin(msg, req);
        });

        // Handle HTTP user info
        vertx.eventBus().consumer("im.logic.USER_INFO", msg -> {
            JsonObject req = (JsonObject) msg.body();
            handleUserInfo(msg, req);
        });

        // Handle friend apply
        vertx.eventBus().consumer("im.logic.FRIEND_APPLY", msg -> {
            JsonObject req = (JsonObject) msg.body();
            handleFriendApply(msg, req);
        });

        // Handle friend list
        vertx.eventBus().consumer("im.logic.FRIEND_LIST", msg -> {
            JsonObject req = (JsonObject) msg.body();
            handleFriendList(msg, req);
        });

        // Handle friend accept
        vertx.eventBus().consumer("im.logic.FRIEND_ACCEPT", msg -> {
            JsonObject req = (JsonObject) msg.body();
            handleFriendAccept(msg, req);
        });

        // Handle friend reject
        vertx.eventBus().consumer("im.logic.FRIEND_REJECT", msg -> {
            JsonObject req = (JsonObject) msg.body();
            handleFriendReject(msg, req);
        });

        // Handle friend request list
        vertx.eventBus().consumer("im.logic.FRIEND_REQUEST_LIST", msg -> {
            JsonObject req = (JsonObject) msg.body();
            handleFriendRequestList(msg, req);
        });

        log.info("AuthVerticle started");
    }

    private void handleLongConnAuth(io.vertx.core.eventbus.Message<Object> msg, byte[] body) {
        try {
            ImProto.AuthRequest req = ImProto.AuthRequest.parseFrom(body);
            String token = req.getToken();

            // Validate JWT + Redis token store
            long userId = validateJwtToken(token);
            if (userId <= 0) {
                ImProto.AuthResponse resp = ImProto.AuthResponse.newBuilder()
                        .setCode(1001).setMsg("invalid token").build();
                msg.reply(resp.toByteArray());
                return;
            }

            // Register route in Redis
            String gatewayId = "gateway-local";
            redisService.setUserOnline(userId, gatewayId, 3600)
                    .onSuccess(v -> log.debug("route set for userId={}", userId))
                    .onFailure(err -> log.warn("route set failed: {}", err.getMessage()));

            ImProto.AuthResponse resp = ImProto.AuthResponse.newBuilder()
                    .setCode(0).setMsg("ok")
                    .setUserInfo(ImProto.UserInfo.newBuilder().setUserId(userId).build())
                    .setServerTime(System.currentTimeMillis())
                    .build();

            msg.reply(resp.toByteArray());
            log.info("long-conn auth success: userId={}", userId);

        } catch (Exception e) {
            log.error("auth error: {}", e.getMessage());
            ImProto.AuthResponse resp = ImProto.AuthResponse.newBuilder()
                    .setCode(1).setMsg("auth error").build();
            msg.reply(resp.toByteArray());
        }
    }

    private void handleRegister(io.vertx.core.eventbus.Message<Object> msg, JsonObject req) {
        String username = req.getString("username");
        String password = req.getString("password");
        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            msg.reply(new JsonObject().put("code", 1003).put("msg", "username and password required"));
            return;
        }

        String salt = generateSalt();
        String passwordHash = hashPassword(password, salt);

        dbService.createUser(username, passwordHash, salt)
                .onSuccess(userId -> {
                    String token = generateJwtToken(userId);
                    long ttlSeconds = serverConfig.getJwtTtlDays() * 24 * 3600L;
                    redisService.storeToken(userId, token, ttlSeconds)
                            .onSuccess(v -> {
                                msg.reply(new JsonObject()
                                        .put("code", 0).put("msg", "ok")
                                        .put("userId", userId).put("token", token));
                                log.info("user registered: {} id={}", username, userId);
                            })
                            .onFailure(err -> msg.reply(new JsonObject().put("code", 1).put("msg", "token store failed")));
                })
                .onFailure(err -> {
                    log.error("register failed: {}", err.getMessage());
                    msg.reply(new JsonObject().put("code", 1).put("msg", "register failed: " + err.getMessage()));
                });
    }

    private void handleLogin(io.vertx.core.eventbus.Message<Object> msg, JsonObject req) {
        String username = req.getString("username");
        String password = req.getString("password");
        if (username == null || password == null) {
            msg.reply(new JsonObject().put("code", 1003).put("msg", "username and password required"));
            return;
        }

        dbService.getUserByUsername(username)
                .onSuccess(user -> {
                    if (user == null) {
                        msg.reply(new JsonObject().put("code", 1004).put("msg", "user not found"));
                        return;
                    }
                    String hash = hashPassword(password, user.getSalt());
                    if (!hash.equals(user.getPasswordHash())) {
                        msg.reply(new JsonObject().put("code", 1001).put("msg", "invalid password"));
                        return;
                    }

                    String token = generateJwtToken(user.getUserId());
                    long ttlSeconds = serverConfig.getJwtTtlDays() * 24 * 3600L;
                    redisService.storeToken(user.getUserId(), token, ttlSeconds)
                            .onSuccess(v -> {
                                msg.reply(new JsonObject()
                                        .put("code", 0).put("msg", "ok")
                                        .put("userId", user.getUserId())
                                        .put("token", token)
                                        .put("nickname", user.getNickname()));
                                log.info("user logged in: {} id={}", username, user.getUserId());
                            })
                            .onFailure(err -> msg.reply(new JsonObject().put("code", 1).put("msg", "token store failed")));
                })
                .onFailure(err -> {
                    log.error("login failed: {}", err.getMessage());
                    msg.reply(new JsonObject().put("code", 1).put("msg", "login failed"));
                });
    }

    private void handleUserInfo(io.vertx.core.eventbus.Message<Object> msg, JsonObject req) {
        long userId = req.getLong("userId", -1L);
        if (userId <= 0) {
            msg.reply(new JsonObject().put("code", 1003).put("msg", "invalid userId"));
            return;
        }

        dbService.getUserById(userId)
                .onSuccess(user -> {
                    if (user == null) {
                        msg.reply(new JsonObject().put("code", 1004).put("msg", "user not found"));
                    } else {
                        msg.reply(new JsonObject()
                                .put("code", 0).put("msg", "ok")
                                .put("userId", user.getUserId())
                                .put("username", user.getUsername())
                                .put("nickname", user.getNickname())
                                .put("avatarUrl", user.getAvatarUrl()));
                    }
                })
                .onFailure(err -> msg.reply(new JsonObject().put("code", 1).put("msg", "db error")));
    }

    private void handleFriendApply(io.vertx.core.eventbus.Message<Object> msg, JsonObject req) {
        long userId = req.getLong("userId", -1L);
        long targetUserId = req.getLong("targetUserId", -1L);
        String message = req.getString("message", "");
        if (userId <= 0 || targetUserId <= 0) {
            msg.reply(new JsonObject().put("code", 1003).put("msg", "invalid params"));
            return;
        }
        dbService.addFriendRequest(userId, targetUserId, message)
                .onSuccess(v -> {
                    // Auto-accept for now
                    dbService.addFriend(userId, targetUserId)
                            .compose(v2 -> dbService.addFriend(targetUserId, userId))
                            .onSuccess(v3 -> msg.reply(new JsonObject().put("code", 0).put("msg", "ok")))
                            .onFailure(err -> msg.reply(new JsonObject().put("code", 1).put("msg", "friend add failed")));
                })
                .onFailure(err -> msg.reply(new JsonObject().put("code", 1).put("msg", "request failed")));
    }

    private void handleFriendList(io.vertx.core.eventbus.Message<Object> msg, JsonObject req) {
        long userId = req.getLong("userId", -1L);
        int limit = req.getInteger("limit", 50);
        if (userId <= 0) {
            msg.reply(new JsonObject().put("code", 1003).put("msg", "invalid userId"));
            return;
        }
        dbService.getFriendList(userId, limit)
                .onSuccess(friends -> {
                    io.vertx.core.json.JsonArray arr = new io.vertx.core.json.JsonArray();
                    for (var u : friends) {
                        arr.add(new JsonObject()
                                .put("userId", u.getUserId())
                                .put("username", u.getUsername())
                                .put("nickname", u.getNickname())
                                .put("avatarUrl", u.getAvatarUrl()));
                    }
                    msg.reply(new JsonObject().put("code", 0).put("msg", "ok").put("friends", arr));
                })
                .onFailure(err -> msg.reply(new JsonObject().put("code", 1).put("msg", "db error")));
    }

    private void handleFriendAccept(io.vertx.core.eventbus.Message<Object> msg, JsonObject req) {
        long userId = req.getLong("userId", -1L); // the one accepting
        long fromUserId = req.getLong("fromUserId", -1L); // the one who sent the request
        if (userId <= 0 || fromUserId <= 0) {
            msg.reply(new JsonObject().put("code", 1003).put("msg", "invalid params"));
            return;
        }
        dbService.acceptFriendRequest(fromUserId, userId)
                .onSuccess(v -> {
                    msg.reply(new JsonObject().put("code", 0).put("msg", "ok"));
                    log.info("friend accepted: {} <-> {}", fromUserId, userId);
                })
                .onFailure(err -> msg.reply(new JsonObject().put("code", 1).put("msg", "accept failed")));
    }

    private void handleFriendReject(io.vertx.core.eventbus.Message<Object> msg, JsonObject req) {
        long userId = req.getLong("userId", -1L);
        long fromUserId = req.getLong("fromUserId", -1L);
        if (userId <= 0 || fromUserId <= 0) {
            msg.reply(new JsonObject().put("code", 1003).put("msg", "invalid params"));
            return;
        }
        dbService.rejectFriendRequest(fromUserId, userId)
                .onSuccess(v -> msg.reply(new JsonObject().put("code", 0).put("msg", "ok")))
                .onFailure(err -> msg.reply(new JsonObject().put("code", 1).put("msg", "reject failed")));
    }

    private void handleFriendRequestList(io.vertx.core.eventbus.Message<Object> msg, JsonObject req) {
        long userId = req.getLong("userId", -1L);
        if (userId <= 0) {
            msg.reply(new JsonObject().put("code", 1003).put("msg", "invalid userId"));
            return;
        }
        dbService.getPendingFriendRequests(userId)
                .onSuccess(users -> {
                    io.vertx.core.json.JsonArray arr = new io.vertx.core.json.JsonArray();
                    for (var u : users) {
                        arr.add(new JsonObject()
                                .put("userId", u.getUserId())
                                .put("username", u.getUsername())
                                .put("nickname", u.getNickname())
                                .put("avatarUrl", u.getAvatarUrl()));
                    }
                    msg.reply(new JsonObject().put("code", 0).put("msg", "ok").put("requests", arr));
                })
                .onFailure(err -> msg.reply(new JsonObject().put("code", 1).put("msg", "db error")));
    }

    private long validateJwtToken(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(jwtKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Long.parseLong(subject);
        } catch (Exception e) {
            return -1;
        }
    }

    private String generateJwtToken(long userId) {
        long now = System.currentTimeMillis();
        long exp = now + serverConfig.getJwtTtlDays() * 24 * 3600 * 1000L;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(exp))
                .signWith(jwtKey)
                .compact();
    }

    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
}
