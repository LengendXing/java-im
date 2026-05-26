package com.im.server.logic;

import com.im.server.common.Cmd;
import com.im.server.common.DatabaseServiceHolder;
import com.im.server.common.RedisServiceHolder;
import com.im.server.common.RequestEnvelope;
import com.im.server.storage.DatabaseService;
import com.im.server.storage.RedisService;
import com.im.protocol.ImProto;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupPullVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(GroupPullVerticle.class);

    private RedisService redisService;
    private DatabaseService dbService;

    @Override
    public void start() {
        redisService = RedisServiceHolder.getInstance();
        dbService = DatabaseServiceHolder.getInstance();

        vertx.eventBus().consumer("im.logic.GROUP_PULL", msg -> {
            JsonObject req = (JsonObject) msg.body();
            handleGroupPull(msg, req);
        });
    }

    private void handleGroupPull(io.vertx.core.eventbus.Message<Object> msg, JsonObject req) {
        long userId = req.getLong("userId", -1L);
        String sessionId = req.getString("sessionId", "");
        long lastSeq = req.getLong("lastSeq", 0L);
        int limit = req.getInteger("limit", 50);

        if (userId <= 0 || sessionId.isEmpty()) {
            msg.reply(new JsonObject().put("code", 1003).put("msg", "invalid params"));
            return;
        }

        dbService.getMessages(sessionId, lastSeq, limit)
                .onSuccess(messages -> {
                    io.vertx.core.json.JsonArray arr = new io.vertx.core.json.JsonArray();
                    for (var m : messages) {
                        arr.add(new JsonObject()
                                .put("msgId", m.getMsgId())
                                .put("sessionId", m.getSessionId())
                                .put("senderId", m.getSenderId())
                                .put("seq", m.getSeq())
                                .put("contentType", m.getContentType())
                                .put("contentText", m.getContentText())
                                .put("contentUrl", m.getContentUrl())
                                .put("serverTime", m.getServerTime()));
                    }
                    msg.reply(new JsonObject().put("code", 0).put("msg", "ok").put("messages", arr));
                    log.debug("group pull: userId={} sessionId={} count={}", userId, sessionId, messages.size());
                })
                .onFailure(err -> msg.reply(new JsonObject().put("code", 1).put("msg", "pull failed")));
    }
}
