package com.im.server.logic;

import com.im.protocol.ImProto;
import com.im.server.common.*;
import com.im.server.storage.DatabaseService;
import com.im.server.storage.Message;
import com.im.server.storage.RedisService;
import com.im.server.storage.Session;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SessionVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(SessionVerticle.class);

    private RedisService redisService;
    private DatabaseService dbService;

    @Override
    public void start() {
        redisService = RedisServiceHolder.getInstance();
        dbService = DatabaseServiceHolder.getInstance();

        vertx.eventBus().consumer("im.logic.SESSION", msg -> {
            byte[] body = (byte[]) msg.body();
            RequestEnvelope envelope = RequestEnvelope.decode(body);
            long userId = envelope.userId;
            int cmd = envelope.cmd;

            if (cmd == Cmd.SESSION_LIST) {
                handleSessionList(msg, userId);
            } else if (cmd == Cmd.MSG_ACK) {
                handleMsgAck(msg, envelope);
            } else {
                log.warn("unknown cmd in SESSION handler: 0x{}", Integer.toHexString(cmd));
            }
        });

        vertx.eventBus().consumer("im.logic.SYNC", msg -> {
            byte[] body = (byte[]) msg.body();
            RequestEnvelope envelope = RequestEnvelope.decode(body);
            handleSync(msg, envelope);
        });
    }

    private void handleSessionList(io.vertx.core.eventbus.Message<Object> msg, long userId) {
        dbService.getSessions(userId, 100)
                .compose(sessions -> {
                    // Merge Redis unread counts for all sessions, then build response
                    return mergeRedisUnread(sessions);
                })
                .onSuccess(respBuilder -> {
                    msg.reply(respBuilder.build().toByteArray());
                    log.debug("session list for userId={}", userId);
                })
                .onFailure(err -> {
                    log.error("session list error: {}", err.getMessage());
                    ImProto.SessionListResponse resp = ImProto.SessionListResponse.newBuilder().build();
                    msg.reply(resp.toByteArray());
                });
    }

    private io.vertx.core.Future<ImProto.SessionListResponse.Builder> mergeRedisUnread(List<Session> sessions) {
        ImProto.SessionListResponse.Builder respBuilder = ImProto.SessionListResponse.newBuilder();

        io.vertx.core.Future<Void> chain = io.vertx.core.Future.succeededFuture();
        for (Session s : sessions) {
            ImProto.SessionInfo.Builder infoBuilder = ImProto.SessionInfo.newBuilder()
                    .setSessionId(s.getSessionId())
                    .setType(s.getType())
                    .setTargetId(s.getTargetId())
                    .setName(s.getName() != null ? s.getName() : "")
                    .setAvatarUrl(s.getAvatarUrl() != null ? s.getAvatarUrl() : "")
                    .setLastMsg(s.getLastMsg() != null ? s.getLastMsg() : "")
                    .setLastMsgTime(s.getLastMsgTime())
                    .setUnreadCount(s.getUnreadCount())
                    .setIsTop(s.isTop())
                    .setIsMuted(s.isMuted());

            final ImProto.SessionInfo.Builder finalInfoBuilder = infoBuilder;
            chain = chain.compose(v ->
                    redisService.getUnread(s.getUserId(), s.getSessionId())
                            .onSuccess(redisUnread -> {
                                if (redisUnread > 0) {
                                    finalInfoBuilder.setUnreadCount((int) (long) redisUnread);
                                }
                            })
                            .mapEmpty()
            ).map(v -> {
                respBuilder.addSessions(finalInfoBuilder);
                return null;
            });
        }
        return chain.map(v -> respBuilder);
    }

    private void handleMsgAck(io.vertx.core.eventbus.Message<Object> msg, RequestEnvelope envelope) {
        try {
            ImProto.MsgAckRequest req = ImProto.MsgAckRequest.parseFrom(envelope.body);
            long userId = envelope.userId;
            String sessionId = req.getSessionId();

            redisService.resetUnread(userId, sessionId)
                    .compose(v -> dbService.resetUnreadCount(userId, sessionId))
                    .onSuccess(v -> {
                        ImProto.GenericResponse resp = ImProto.GenericResponse.newBuilder()
                                .setCode(0).setMsg("ok").build();
                        msg.reply(resp.toByteArray());
                        log.debug("msg ack: userId={} session={}", userId, sessionId);
                    })
                    .onFailure(err -> {
                        log.error("msg ack error: {}", err.getMessage());
                        ImProto.GenericResponse resp = ImProto.GenericResponse.newBuilder()
                                .setCode(1).setMsg("ack failed").build();
                        msg.reply(resp.toByteArray());
                    });
        } catch (Exception e) {
            log.error("msg ack parse error: {}", e.getMessage());
        }
    }

    private void handleSync(io.vertx.core.eventbus.Message<Object> msg, RequestEnvelope envelope) {
        try {
            ImProto.SyncRequest req = ImProto.SyncRequest.parseFrom(envelope.body);
            int limit = req.getLimit() > 0 ? req.getLimit() : 50;

            ImProto.SyncResponse.Builder respBuilder = ImProto.SyncResponse.newBuilder();

            syncPoints(envelope.userId, req.getPointsList(), limit, 0, respBuilder)
                    .onSuccess(v -> msg.reply(respBuilder.build().toByteArray()))
                    .onFailure(err -> {
                        log.error("sync error: {}", err.getMessage());
                        msg.reply(ImProto.SyncResponse.newBuilder().build().toByteArray());
                    });

        } catch (Exception e) {
            log.error("sync parse error: {}", e.getMessage());
            msg.reply(ImProto.SyncResponse.newBuilder().build().toByteArray());
        }
    }

    private io.vertx.core.Future<Void> syncPoints(long userId, List<ImProto.SyncPoint> points, int limit,
                                                   int index, ImProto.SyncResponse.Builder respBuilder) {
        if (index >= points.size()) {
            return io.vertx.core.Future.succeededFuture();
        }

        ImProto.SyncPoint point = points.get(index);
        String sessionId = point.getSessionId();
        long lastSeq = point.getLastSeq();

        return dbService.getMessages(sessionId, lastSeq, limit)
                .compose(messages -> {
                    ImProto.SessionMessages.Builder smBuilder = ImProto.SessionMessages.newBuilder()
                            .setSessionId(sessionId);

                    for (Message m : messages) {
                        ImProto.Message.Builder msgBuilder = ImProto.Message.newBuilder()
                                .setMsgId(m.getMsgId())
                                .setSenderId(m.getSenderId())
                                .setSeq(m.getSeq())
                                .setServerTime(m.getServerTime());

                        ImProto.MessageContent.Builder contentBuilder = ImProto.MessageContent.newBuilder()
                                .setMsgType(m.getContentType());
                        if (m.getContentText() != null) contentBuilder.setText(m.getContentText());
                        if (m.getContentUrl() != null) contentBuilder.setUrl(m.getContentUrl());
                        if (m.getContentExtra() != null) contentBuilder.setExtra(m.getContentExtra());
                        msgBuilder.setContent(contentBuilder);

                        smBuilder.addMessages(msgBuilder);
                    }

                    smBuilder.setHasMore(messages.size() >= limit);
                    respBuilder.addSessions(smBuilder);

                    return syncPoints(userId, points, limit, index + 1, respBuilder);
                });
    }
}
