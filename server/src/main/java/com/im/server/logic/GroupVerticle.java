package com.im.server.logic;

import com.im.protocol.ImProto;
import com.im.server.common.*;
import com.im.server.storage.DatabaseService;
import com.im.server.storage.Message;
import com.im.server.storage.RedisService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class GroupVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(GroupVerticle.class);

    private RedisService redisService;
    private DatabaseService dbService;

    @Override
    public void start() {
        redisService = RedisServiceHolder.getInstance();
        dbService = DatabaseServiceHolder.getInstance();

        vertx.eventBus().consumer("im.logic.GROUP_MSG", msg -> {
            byte[] body = (byte[]) msg.body();
            handleGroupMsg(msg, body);
        });

        vertx.eventBus().consumer("im.logic.GROUP_CREATE", msg -> {
            JsonObject req = (JsonObject) msg.body();
            handleGroupCreate(msg, req);
        });

        vertx.eventBus().consumer("im.logic.GROUP_MEMBER_LIST", msg -> {
            JsonObject req = (JsonObject) msg.body();
            handleGroupMemberList(msg, req);
        });
    }

    private void handleGroupMsg(io.vertx.core.eventbus.Message<Object> msg, byte[] body) {
        try {
            RequestEnvelope envelope = RequestEnvelope.decode(body);
            long senderId = envelope.userId;
            ImProto.GroupMsgRequest req = ImProto.GroupMsgRequest.parseFrom(envelope.body);
            long groupId = req.getGroupId();
            String sessionId = "group_" + groupId;

            // 1. Generate seq via Redis INCR
            redisService.incrSeq(sessionId)
                    .compose(seq -> {
                        long msgId = SnowflakeIdHolder.nextId();
                        long serverTime = System.currentTimeMillis();

                        // 2. Persist message (one copy for group session)
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
                                    // 3. Query group members
                                    return dbService.getGroupMembers(groupId);
                                })
                                .map(members -> new GroupResult(msgId, seq, serverTime, sessionId, senderId, groupId, content, members, req.getAtUserIdsList()));
                    })
                    .onSuccess(result -> {
                        // 4. ACK to sender
                        ImProto.GroupMsgResponse ack = ImProto.GroupMsgResponse.newBuilder()
                                .setCode(0).setMsg("ok")
                                .setMsgId(result.msgId).setSeq(result.seq)
                                .setServerTime(result.serverTime)
                                .build();
                        msg.reply(ack.toByteArray());

                        // 5. Push to all online group members, INCR unread for offline
                        for (Long memberId : result.members) {
                            if (memberId == result.senderId) {
                                // Update sender session but don't push to self
                                String lastMsg = result.content.getText().length() > 50
                                        ? result.content.getText().substring(0, 50) : result.content.getText();
                                dbService.upsertSession(memberId, result.sessionId, 2, result.groupId, "",
                                        lastMsg, result.serverTime);
                                continue;
                            }

                            // Update member session
                            String lastMsg = result.content.getText().length() > 50
                                    ? result.content.getText().substring(0, 50) : result.content.getText();
                            dbService.upsertSession(memberId, result.sessionId, 2, result.groupId, "",
                                    lastMsg, result.serverTime);

                            // Push notify
                            ImProto.GroupMsgNotify notify = ImProto.GroupMsgNotify.newBuilder()
                                    .setMsgId(result.msgId).setSenderId(result.senderId)
                                    .setGroupId(result.groupId).setSessionId(result.sessionId)
                                    .setSeq(result.seq).setContent(result.content)
                                    .addAllAtUserIds(result.atUserIds)
                                    .setServerTime(result.serverTime)
                                    .build();
                            vertx.eventBus().send("im.logic.PUSH",
                                    PushEnvelope.create(memberId, Cmd.GROUP_MSG_NOTIFY, notify.toByteArray()));

                            // INCR unread for all non-sender members
                            redisService.incrUnread(memberId, result.sessionId);
                            dbService.incrUnreadCount(memberId, result.sessionId);
                        }

                        log.info("GROUP msg: sender={} group={} seq={} members={}", result.senderId, result.groupId, result.seq, result.members.size());

                    })
                    .onFailure(err -> {
                        log.error("GROUP msg error: {}", err.getMessage());
                        ImProto.GroupMsgResponse ack = ImProto.GroupMsgResponse.newBuilder()
                                .setCode(1).setMsg("send failed: " + err.getMessage()).build();
                        msg.reply(ack.toByteArray());
                    });

        } catch (Exception e) {
            log.error("GROUP msg parse error: {}", e.getMessage());
            ImProto.GroupMsgResponse ack = ImProto.GroupMsgResponse.newBuilder()
                    .setCode(1).setMsg("parse error").build();
            msg.reply(ack.toByteArray());
        }
    }

    private void handleGroupCreate(io.vertx.core.eventbus.Message<Object> msg, JsonObject req) {
        long ownerId = req.getLong("userId", -1L);
        String name = req.getString("name", "");
        if (ownerId <= 0 || name.isEmpty()) {
            msg.reply(new JsonObject().put("code", 1003).put("msg", "invalid params"));
            return;
        }

        dbService.createGroup(name, ownerId)
                .compose(groupId -> {
                    // Add owner as member with role=2 (owner)
                    return dbService.addGroupMember(groupId, ownerId, 2)
                            .map(v -> groupId);
                })
                .onSuccess(groupId -> {
                    msg.reply(new JsonObject().put("code", 0).put("msg", "ok").put("groupId", groupId));
                    log.info("group created: {} by userId={}", name, ownerId);
                })
                .onFailure(err -> {
                    msg.reply(new JsonObject().put("code", 1).put("msg", "create failed"));
                    log.error("group create error: {}", err.getMessage());
                });
    }

    private void handleGroupMemberList(io.vertx.core.eventbus.Message<Object> msg, JsonObject req) {
        long groupId = req.getLong("groupId", -1L);
        if (groupId <= 0) {
            msg.reply(new JsonObject().put("code", 1003).put("msg", "invalid groupId"));
            return;
        }

        dbService.getGroupMembers(groupId)
                .onSuccess(members -> {
                    io.vertx.core.json.JsonArray arr = new io.vertx.core.json.JsonArray();
                    for (Long uid : members) {
                        arr.add(uid);
                    }
                    msg.reply(new JsonObject().put("code", 0).put("msg", "ok").put("members", arr));
                })
                .onFailure(err -> msg.reply(new JsonObject().put("code", 1).put("msg", "db error")));
    }

    private record GroupResult(long msgId, long seq, long serverTime, String sessionId,
                                long senderId, long groupId, ImProto.MessageContent content,
                                List<Long> members, List<Long> atUserIds) {}
}
