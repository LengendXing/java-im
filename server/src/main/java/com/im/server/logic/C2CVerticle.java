package com.im.server.logic;

import com.im.protocol.ImProto;
import com.im.server.common.*;
import com.im.server.mq.FolkmqServiceHolder;
import com.im.server.mq.FolkmqService;
import com.im.server.storage.DatabaseService;
import com.im.server.storage.Message;
import com.im.server.storage.RedisService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class C2CVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(C2CVerticle.class);

    private RedisService redisService;
    private DatabaseService dbService;

    @Override
    public void start() {
        redisService = RedisServiceHolder.getInstance();
        dbService = DatabaseServiceHolder.getInstance();

        vertx.eventBus().consumer("im.logic.C2C_MSG", msg -> {
            byte[] body = (byte[]) msg.body();
            handleC2CMsg(msg, body);
        });
    }

    private void handleC2CMsg(io.vertx.core.eventbus.Message<Object> msg, byte[] body) {
        try {
            RequestEnvelope envelope = RequestEnvelope.decode(body);
            long senderId = envelope.userId;
            ImProto.C2CMsgRequest req = ImProto.C2CMsgRequest.parseFrom(envelope.body);
            long receiverId = req.getReceiverId();
            String sessionId = generateSessionId(senderId, receiverId);

            // 1. Generate seq via Redis INCR
            redisService.incrSeq(sessionId)
                    .compose(seq -> {
                        // 2. Generate msgId via Snowflake
                        long msgId = SnowflakeIdHolder.nextId();
                        long serverTime = System.currentTimeMillis();

                        // 3. Persist message to MySQL
                        ImProto.MessageContent content = req.getContent();
                        Message dbMsg = new Message();
                        dbMsg.setMsgId(msgId);
                        dbMsg.setSessionId(sessionId);
                        dbMsg.setSenderId(senderId);
                        dbMsg.setSeq(seq);
                        dbMsg.setContentType(content.getMsgType());
                        dbMsg.setContentText(content.getText());
                        dbMsg.setContentUrl(content.getUrl());
                        dbMsg.setContentExtra(content.getExtra());
                        dbMsg.setClientMsgId(req.getClientMsgId());
                        dbMsg.setServerTime(serverTime);

                        return dbService.insertMessage(dbMsg)
                                .compose(v -> {
                                    // 4. Update sender's session
                                    String lastMsgText = content.getText().length() > 50
                                            ? content.getText().substring(0, 50) : content.getText();
                                    return dbService.upsertSession(senderId, sessionId, 1, receiverId, "",
                                            lastMsgText, serverTime);
                                })
                                .compose(v -> {
                                    // 5. Update receiver's session
                                    String lastMsgText = req.getContent().getText().length() > 50
                                            ? req.getContent().getText().substring(0, 50) : req.getContent().getText();
                                    return dbService.upsertSession(receiverId, sessionId, 1, senderId, "",
                                            lastMsgText, serverTime);
                                })
                                .compose(v -> {
                                    // 6. INCR receiver unread count in Redis
                                    return redisService.incrUnread(receiverId, sessionId);
                                })
                                .compose(v -> {
                                    // Also incr unread in DB for persistence
                                    return dbService.incrUnreadCount(receiverId, sessionId);
                                })
                                .map(v -> new C2CResult(msgId, seq, serverTime, sessionId, senderId, receiverId, content));
                    })
                    .onSuccess(result -> {
                        // 7. ACK to sender
                        ImProto.C2CMsgResponse ack = ImProto.C2CMsgResponse.newBuilder()
                                .setCode(0).setMsg("ok")
                                .setMsgId(result.msgId).setSeq(result.seq)
                                .setServerTime(result.serverTime)
                                .build();
                        msg.reply(ack.toByteArray());

                        // 8. Push to receiver via Folkmq (persistent, ordered by session)
                        ImProto.C2CMsgNotify notify = ImProto.C2CMsgNotify.newBuilder()
                                .setMsgId(result.msgId).setSenderId(result.senderId)
                                .setSessionId(result.sessionId).setSeq(result.seq)
                                .setContent(result.content).setServerTime(result.serverTime)
                                .build();
                        byte[] pushData = PushEnvelope.create(result.receiverId, Cmd.C2C_MSG_NOTIFY, notify.toByteArray());

                        FolkmqService folkmq = FolkmqServiceHolder.getInstance();
                        if (folkmq != null) {
                            folkmq.publishOrdered("im-c2c", pushData, result.sessionId);
                        } else {
                            vertx.eventBus().send("im.logic.PUSH", pushData);
                        }

                        log.info("C2C msg: {} -> {}, seq={}", result.senderId, result.receiverId, result.seq);
                    })
                    .onFailure(err -> {
                        log.error("C2C msg error: {}", err.getMessage());
                        ImProto.C2CMsgResponse ack = ImProto.C2CMsgResponse.newBuilder()
                                .setCode(1).setMsg("send failed").build();
                        msg.reply(ack.toByteArray());
                    });

        } catch (Exception e) {
            log.error("C2C msg parse error: {}", e.getMessage());
            ImProto.C2CMsgResponse ack = ImProto.C2CMsgResponse.newBuilder()
                    .setCode(1).setMsg("parse error").build();
            msg.reply(ack.toByteArray());
        }
    }

    private String generateSessionId(long userId1, long userId2) {
        long min = Math.min(userId1, userId2);
        long max = Math.max(userId1, userId2);
        return "c2c_" + min + "_" + max;
    }

    private record C2CResult(long msgId, long seq, long serverTime, String sessionId,
                             long senderId, long receiverId, ImProto.MessageContent content) {}
}
