package com.im.server.e2ee;

import com.im.protocol.ImProto;
import com.im.server.common.DatabaseServiceHolder;
import com.im.server.common.RedisServiceHolder;
import com.im.server.common.RequestEnvelope;
import com.im.server.storage.DatabaseService;
import com.im.server.storage.RedisService;
import io.vertx.core.AbstractVerticle;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class E2eeVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(E2eeVerticle.class);
    private static final String PUSH_TOKEN_KEY = "im:push_token:";

    @Override
    public void start() {
        vertx.eventBus().consumer("im.logic.KEY_BUNDLE_REQUEST", msg -> {
            byte[] body = (byte[]) msg.body();
            handleKeyBundleRequest(msg, body);
        });

        vertx.eventBus().consumer("im.logic.PUSH_TOKEN_REGISTER", msg -> {
            byte[] body = (byte[]) msg.body();
            handlePushTokenRegister(msg, body);
        });

        log.info("E2eeVerticle started");
    }

    private void handleKeyBundleRequest(io.vertx.core.eventbus.Message<Object> msg, byte[] body) {
        try {
            RequestEnvelope envelope = RequestEnvelope.decode(body);
            ImProto.KeyBundleRequest req = ImProto.KeyBundleRequest.parseFrom(envelope.body);
            long targetUserId = req.getTargetUserId();

            DatabaseService db = DatabaseServiceHolder.getInstance();
            db.getPool()
                .preparedQuery("SELECT key_type, key_id, public_key, signature FROM im_user_key WHERE user_id = ? AND (key_type IN (1, 2) OR (key_type = 3 AND used = 0)) ORDER BY key_type, key_id LIMIT 10")
                .execute(Tuple.of(targetUserId))
                .onSuccess(rows -> {
                    ImProto.KeyBundleResponse.Builder respBuilder = ImProto.KeyBundleResponse.newBuilder()
                            .setCode(0).setMsg("ok");

                    ImProto.OneTimePreKey otpk = null;
                    for (Row row : rows) {
                        int keyType = row.getInteger("key_type");
                        byte[] pubKey = row.getBuffer("public_key") != null ? row.getBuffer("public_key").getBytes() : new byte[0];
                        byte[] sig = row.getBuffer("signature") != null ? row.getBuffer("signature").getBytes() : new byte[0];

                        switch (keyType) {
                            case 1 -> respBuilder.setIdentityKey(com.google.protobuf.ByteString.copyFrom(pubKey));
                            case 2 -> {
                                respBuilder.setSignedPrekey(com.google.protobuf.ByteString.copyFrom(pubKey));
                                respBuilder.setSignedPrekeySignature(com.google.protobuf.ByteString.copyFrom(sig));
                            }
                            case 3 -> {
                                if (otpk == null) {
                                    otpk = ImProto.OneTimePreKey.newBuilder()
                                            .setKeyId(row.getInteger("key_id"))
                                            .setPublicKey(com.google.protobuf.ByteString.copyFrom(pubKey))
                                            .build();
                                }
                            }
                        }
                    }
                    if (otpk != null) {
                        respBuilder.setOneTimePrekey(otpk);
                        db.getPool()
                            .preparedQuery("UPDATE im_user_key SET used = 1 WHERE user_id = ? AND key_type = 3 AND key_id = ?")
                            .execute(Tuple.of(targetUserId, otpk.getKeyId()));
                    }

                    ImProto.KeyBundleResponse resp = respBuilder.build();
                    msg.reply(resp.toByteArray());
                    log.debug("key bundle sent for targetUserId={}", targetUserId);
                })
                .onFailure(err -> {
                    log.warn("key bundle lookup failed: {}", err.getMessage());
                    ImProto.KeyBundleResponse resp = ImProto.KeyBundleResponse.newBuilder()
                            .setCode(1).setMsg("lookup failed").build();
                    msg.reply(resp.toByteArray());
                });
        } catch (Exception e) {
            log.error("key bundle request error: {}", e.getMessage());
            ImProto.KeyBundleResponse resp = ImProto.KeyBundleResponse.newBuilder()
                    .setCode(1).setMsg("parse error").build();
            msg.reply(resp.toByteArray());
        }
    }

    private void handlePushTokenRegister(io.vertx.core.eventbus.Message<Object> msg, byte[] body) {
        try {
            RequestEnvelope envelope = RequestEnvelope.decode(body);
            ImProto.PushTokenRegisterRequest req = ImProto.PushTokenRegisterRequest.parseFrom(envelope.body);
            long userId = envelope.userId;
            int platform = req.getPlatform();
            String token = req.getToken();
            String bundleId = req.getBundleId();

            if (token == null || token.isEmpty()) {
                ImProto.PushTokenRegisterResponse resp = ImProto.PushTokenRegisterResponse.newBuilder()
                        .setCode(1003).setMsg("token required").build();
                msg.reply(resp.toByteArray());
                return;
            }

            DatabaseService db = DatabaseServiceHolder.getInstance();
            db.upsertPushToken(userId, platform, token, bundleId)
                    .onSuccess(v -> {
                        RedisService redis = RedisServiceHolder.getInstance();
                        if (redis != null) {
                            redis.getRedisAPI().setex(
                                    PUSH_TOKEN_KEY + userId + ":" + platform,
                                    String.valueOf(86400),
                                    token
                            );
                        }

                        ImProto.PushTokenRegisterResponse resp = ImProto.PushTokenRegisterResponse.newBuilder()
                                .setCode(0).setMsg("ok").build();
                        msg.reply(resp.toByteArray());
                        log.info("push token registered: userId={} platform={}", userId, platform);
                    })
                    .onFailure(err -> {
                        log.warn("push token register failed: {}", err.getMessage());
                        ImProto.PushTokenRegisterResponse resp = ImProto.PushTokenRegisterResponse.newBuilder()
                                .setCode(1).setMsg("db error").build();
                        msg.reply(resp.toByteArray());
                    });
        } catch (Exception e) {
            log.error("push token register error: {}", e.getMessage());
            ImProto.PushTokenRegisterResponse resp = ImProto.PushTokenRegisterResponse.newBuilder()
                    .setCode(1).setMsg("parse error").build();
            msg.reply(resp.toByteArray());
        }
    }
}
