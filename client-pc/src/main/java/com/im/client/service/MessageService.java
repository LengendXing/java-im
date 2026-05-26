package com.im.client.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.im.client.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for message operations via HTTP: recall, mark read.
 */
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static MessageService instance;
    private final Gson gson = new Gson();

    private MessageService() {}

    public static synchronized MessageService getInstance() {
        if (instance == null) {
            instance = new MessageService();
        }
        return instance;
    }

    /**
     * Recall a message (within 2 minutes of sending).
     * @return true on success
     */
    public boolean recall(long msgId, String sessionId) {
        JsonObject body = new JsonObject();
        body.addProperty("msgId", msgId);
        body.addProperty("sessionId", sessionId);

        String resp = AuthService.getInstance().httpPost(
                Config.getHttpBaseUrl() + "/api/message/recall", body.toString());
        if (resp != null) {
            try {
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                int code = json.has("code") ? json.get("code").getAsInt() : -1;
                if (code == 0) {
                    log.info("Message recalled: msgId={}", msgId);
                    return true;
                }
            } catch (Exception e) {
                log.error("Parse recall response failed: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * Mark messages as read up to lastReadSeq for a session.
     * @return true on success
     */
    public boolean markRead(String sessionId, long lastReadSeq) {
        JsonObject body = new JsonObject();
        body.addProperty("sessionId", sessionId);
        body.addProperty("lastReadSeq", lastReadSeq);

        String resp = AuthService.getInstance().httpPost(
                Config.getHttpBaseUrl() + "/api/message/read", body.toString());
        if (resp != null) {
            try {
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                int code = json.has("code") ? json.get("code").getAsInt() : -1;
                if (code == 0) {
                    log.info("Messages marked read: sessionId={}, seq={}", sessionId, lastReadSeq);
                    return true;
                }
            } catch (Exception e) {
                log.error("Parse read response failed: {}", e.getMessage());
            }
        }
        return false;
    }
}
