package com.im.client.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.im.client.util.Config;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for friend request operations: list pending, accept, reject.
 */
public class FriendService {

    private static final Logger log = LoggerFactory.getLogger(FriendService.class);
    private static FriendService instance;
    private final Gson gson = new Gson();

    /** Model for a friend request row. */
    public static class FriendRequest {
        private final long fromUserId;
        private final String fromUsername;
        private final String fromNickname;
        private final long requestTime;

        public FriendRequest(long fromUserId, String fromUsername, String fromNickname, long requestTime) {
            this.fromUserId = fromUserId;
            this.fromUsername = fromUsername;
            this.fromNickname = fromNickname;
            this.requestTime = requestTime;
        }

        public long getFromUserId() { return fromUserId; }
        public String getFromUsername() { return fromUsername; }
        public String getFromNickname() { return fromNickname; }
        public long getRequestTime() { return requestTime; }

        @Override
        public String toString() {
            String name = (fromNickname != null && !fromNickname.isEmpty()) ? fromNickname : fromUsername;
            return name + " (" + fromUsername + ")";
        }
    }

    private final ObservableList<FriendRequest> pendingRequests = FXCollections.observableArrayList();

    private FriendService() {}

    public static synchronized FriendService getInstance() {
        if (instance == null) {
            instance = new FriendService();
        }
        return instance;
    }

    public ObservableList<FriendRequest> getPendingRequests() {
        return pendingRequests;
    }

    public void loadPendingRequests() {
        new Thread(() -> {
            String resp = AuthService.getInstance().httpGet(
                    Config.getHttpBaseUrl() + "/api/friend/requests");
            if (resp == null) {
                log.warn("Failed to load friend requests");
                return;
            }
            try {
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                int code = json.has("code") ? json.get("code").getAsInt() : -1;
                if (code == 0 && json.has("data")) {
                    JsonArray arr = json.getAsJsonArray("data");
                    Platform.runLater(() -> {
                        pendingRequests.clear();
                        for (int i = 0; i < arr.size(); i++) {
                            JsonObject obj = arr.get(i).getAsJsonObject();
                            long fromUserId = obj.has("fromUserId") ? obj.get("fromUserId").getAsLong() : 0;
                            String fromUsername = obj.has("fromUsername") ? obj.get("fromUsername").getAsString() : "";
                            String fromNickname = obj.has("fromNickname") ? obj.get("fromNickname").getAsString() : "";
                            long requestTime = obj.has("requestTime") ? obj.get("requestTime").getAsLong() : 0;
                            pendingRequests.add(new FriendRequest(fromUserId, fromUsername, fromNickname, requestTime));
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Parse friend requests failed: {}", e.getMessage());
            }
        }).start();
    }

    public boolean accept(long fromUserId) {
        JsonObject body = new JsonObject();
        body.addProperty("fromUserId", fromUserId);
        String resp = AuthService.getInstance().httpPost(
                Config.getHttpBaseUrl() + "/api/friend/accept", body.toString());
        if (resp != null) {
            try {
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                int code = json.has("code") ? json.get("code").getAsInt() : -1;
                if (code == 0) {
                    Platform.runLater(() -> pendingRequests.removeIf(r -> r.getFromUserId() == fromUserId));
                    log.info("Friend request accepted: fromUserId={}", fromUserId);
                    return true;
                }
            } catch (Exception e) {
                log.error("Parse accept response failed: {}", e.getMessage());
            }
        }
        return false;
    }

    public boolean reject(long fromUserId) {
        JsonObject body = new JsonObject();
        body.addProperty("fromUserId", fromUserId);
        String resp = AuthService.getInstance().httpPost(
                Config.getHttpBaseUrl() + "/api/friend/reject", body.toString());
        if (resp != null) {
            try {
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                int code = json.has("code") ? json.get("code").getAsInt() : -1;
                if (code == 0) {
                    Platform.runLater(() -> pendingRequests.removeIf(r -> r.getFromUserId() == fromUserId));
                    log.info("Friend request rejected: fromUserId={}", fromUserId);
                    return true;
                }
            } catch (Exception e) {
                log.error("Parse reject response failed: {}", e.getMessage());
            }
        }
        return false;
    }
}
