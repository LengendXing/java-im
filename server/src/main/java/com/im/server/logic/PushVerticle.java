package com.im.server.logic;

import com.im.server.common.Cmd;
import com.im.server.common.RedisServiceHolder;
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

        vertx.eventBus().consumer("im.logic.PUSH", msg -> {
            byte[] data = (byte[]) msg.body();
            handlePush(data);
        });
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

            // Look up user route via Redis to find correct gateway
            redisService.getRoute(userId)
                    .onSuccess(gatewayId -> {
                        if (gatewayId != null) {
                            Buffer pushBuf = Buffer.buffer();
                            pushBuf.appendLong(userId);
                            pushBuf.appendBytes(packetBytes);

                            // Route to the specific gateway instance
                            String address = "im.gateway." + gatewayId;
                            vertx.eventBus().send(address, pushBuf.getBytes());

                            log.debug("push to userId={}, cmd=0x{}, gateway={}", userId, Integer.toHexString(cmd), gatewayId);
                        } else {
                            log.debug("user {} offline, skip push for cmd=0x{}", userId, Integer.toHexString(cmd));
                        }
                    })
                    .onFailure(err -> {
                        log.warn("route lookup failed for userId={}: {}", userId, err.getMessage());
                        // Fallback: broadcast to local gateway
                        Buffer pushBuf = Buffer.buffer();
                        pushBuf.appendLong(userId);
                        pushBuf.appendBytes(packetBytes);
                        vertx.eventBus().send("im.gateway.push", pushBuf.getBytes());
                    });

        } catch (Exception e) {
            log.error("push error: {}", e.getMessage());
        }
    }
}
