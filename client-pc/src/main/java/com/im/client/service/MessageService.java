package com.im.client.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.im.client.util.Config;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Service for message operations via HTTP: recall, mark read, search.
 */
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static MessageService instance;
    private final Gson gson = new Gson();

    public static class SearchResult {
        private final long msgId;
        private final String sessionId;
        private final String content;
        private final long senderId;
        private final long serverTime;

        public SearchResult(long msgId, String sessionId, String content, long senderId, long serverTime) {
            this.msgId = msgId;
            this.sessionId = sessionId;
            this.content = content;
            this.senderId = senderId;
            this.serverTime = serverTime;
        }

        public long getMsgId() { return msgId; }
        public String getSessionId() { return sessionId; }
        public String getContent() { return content; }
        public long getSenderId() { return senderId; }
        public long getServerTime() { return serverTime; }

        @Override
        public String toString() { return content; }
    }

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

    public ObservableList<SearchResult> searchMessages(String keyword) {
        ObservableList<SearchResult> results = FXCollections.observableArrayList();
        try {
            String url = Config.getHttpBaseUrl() + "/api/message/search?q=" +
                    URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&limit=20";
            String resp = AuthService.getInstance().httpGet(url);
            if (resp == null) return results;
            JsonObject json = gson.fromJson(resp, JsonObject.class);
            int code = json.has("code") ? json.get("code").getAsInt() : -1;
            if (code == 0 && json.has("data")) {
                JsonArray arr = json.getAsJsonArray("data");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    long msgId = obj.has("msgId") ? obj.get("msgId").getAsLong() : 0;
                    String sid = obj.has("sessionId") ? obj.get("sessionId").getAsString() : "";
                    String content = obj.has("contentText") ? obj.get("contentText").getAsString() : "";
                    long senderId = obj.has("senderId") ? obj.get("senderId").getAsLong() : 0;
                    long serverTime = obj.has("serverTime") ? obj.get("serverTime").getAsLong() : 0;
                    results.add(new SearchResult(msgId, sid, content, senderId, serverTime));
                }
            }
        } catch (Exception e) {
            log.error("Search messages failed: {}", e.getMessage());
        }
        return results;
    }
}
