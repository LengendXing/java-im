package com.im.server.logic;

import com.im.server.common.Cmd;
import com.im.server.common.RedisServiceHolder;
import com.im.server.e2ee.E2eeKeyServiceHolder;
import com.im.server.e2ee.E2eeKeyService;
import com.im.server.mq.FolkmqServiceHolder;
import com.im.server.mq.FolkmqService;
import com.im.server.push.ApnsService;
import com.im.server.push.FcmService;
import com.im.server.push.PushServiceHolder;
import com.im.server.push.PushRateLimiter;
import com.im.server.common.DatabaseServiceHolder;
import com.im.server.storage.DatabaseService;
import com.im.server.storage.RedisService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class PushVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(PushVerticle.class);

    private RedisService redisService;

    @Override
    public void start() {
        redisService = RedisServiceHolder.getInstance();

        // Subscribe to Folkmq topics for persistent message delivery
        FolkmqService folkmq = FolkmqServiceHolder.getInstance();
        if (folkmq != null) {
            folkmq.subscribe("im-c2c", this::handleFolkmqMessage);
            folkmq.subscribe("im-group", this::handleFolkmqMessage);
            log.info("PushVerticle subscribed to Folkmq topics im-c2c, im-group");
        }

        // Keep EventBus consumer for backward compatibility and non-Folkmq pushes
        vertx.eventBus().consumer("im.logic.PUSH", msg -> {
            byte[] data = (byte[]) msg.body();
            handlePush(data);
        });
    }

    private void handleFolkmqMessage(byte[] data) {
        handlePush(data);
    }

    private void handlePush(byte[] data) {
        try {
            PushEnvelope envelope = PushEnvelope.decode(data);
            long userId = envelope.userId;
            int cmd = envelope.cmd;
            byte[] payload = envelope.payload;

            int totalLen = Cmd.HEADER_LEN + payload.length;
            ByteBuffer buf = ByteBuffer.allocate(totalLen);
            buf.putShort((short) Cmd.MAGIC);
            buf.putShort((short) Cmd.VERSION);
            buf.putShort((short) cmd);
            buf.put((byte) 2); // NOTIFY
            buf.putInt(0); // sequenceId
            buf.putInt(payload.length);
            buf.put(new byte[7]); // padding
            buf.put(payload);

            byte[] packetBytes = buf.array();

            redisService.getRoute(userId)
                    .onSuccess(gatewayId -> {
                        if (gatewayId != null) {
                            Buffer pushBuf = Buffer.buffer();
                            pushBuf.appendLong(userId);
                            pushBuf.appendBytes(packetBytes);

                            String address = "im.gateway." + gatewayId;
                            vertx.eventBus().send(address, pushBuf.getBytes());

                            log.debug("push to userId={}, cmd=0x{}, gateway={}", userId, Integer.toHexString(cmd), gatewayId);
                        } else {
                            log.debug("user {} offline, sending push notification", userId);
                            sendOfflinePush(userId, cmd, payload);
                        }
                    })
                    .onFailure(err -> {
                        log.warn("route lookup failed for userId={}: {}", userId, err.getMessage());
                        Buffer pushBuf = Buffer.buffer();
                        pushBuf.appendLong(userId);
                        pushBuf.appendBytes(packetBytes);
                        vertx.eventBus().send("im.gateway.push", pushBuf.getBytes());
                    });

        } catch (Exception e) {
            log.error("push error: {}", e.getMessage());
        }
    }

    private void sendOfflinePush(long userId, int cmd, byte[] payload) {
        PushRateLimiter limiter = PushServiceHolder.getRateLimiter();
        if (limiter != null && !limiter.shouldPush(userId)) return;

        DatabaseService db = DatabaseServiceHolder.getInstance();
        ApnsService apns = PushServiceHolder.getApnsService();
        FcmService fcm = PushServiceHolder.getFcmService();

        String title = "新消息";
        String body = "你有一条新消息";

        // Try iOS (platform=1) then Android (platform=2)
        if (apns != null && apns.isEnabled()) {
            db.getPushToken(userId, 1).onSuccess(token -> {
                if (token != null) apns.push(token, title, body, 1);
            });
        }
        if (fcm != null && fcm.isEnabled()) {
            db.getPushToken(userId, 2).onSuccess(token -> {
                if (token != null) fcm.push(token, title, body);
            });
        }
    }
}
