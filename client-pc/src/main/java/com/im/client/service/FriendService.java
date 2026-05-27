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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Service for friend operations: list, search, apply, request management.
 */
public class FriendService {

    private static final Logger log = LoggerFactory.getLogger(FriendService.class);
    private static FriendService instance;
    private final Gson gson = new Gson();

    /** Model for a friend row. */
    public static class FriendItem {
        private final long userId;
        private final String username;
        private final String nickname;
        private final String avatarUrl;

        public FriendItem(long userId, String username, String nickname, String avatarUrl) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.avatarUrl = avatarUrl;
        }

        public long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getNickname() { return nickname; }
        public String getAvatarUrl() { return avatarUrl; }

        @Override
        public String toString() {
            String name = (nickname != null && !nickname.isEmpty()) ? nickname : username;
            return name + " (" + userId + ")";
        }
    }

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
    private final ObservableList<FriendItem> friendList = FXCollections.observableArrayList();

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

    public ObservableList<FriendItem> getFriendList() {
        return friendList;
    }

    public void loadFriendList() {
        new Thread(() -> {
            String resp = AuthService.getInstance().httpGet(
                    Config.getHttpBaseUrl() + "/api/friend/list");
            if (resp == null) return;
            try {
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                int code = json.has("code") ? json.get("code").getAsInt() : -1;
                if (code == 0 && json.has("data")) {
                    JsonArray arr = json.getAsJsonArray("data");
                    Platform.runLater(() -> {
                        friendList.clear();
                        for (int i = 0; i < arr.size(); i++) {
                            JsonObject obj = arr.get(i).getAsJsonObject();
                            long uid = obj.has("userId") ? obj.get("userId").getAsLong() : 0;
                            String uname = obj.has("username") ? obj.get("username").getAsString() : "";
                            String nick = obj.has("nickname") ? obj.get("nickname").getAsString() : "";
                            String avatar = obj.has("avatarUrl") ? obj.get("avatarUrl").getAsString() : "";
                            friendList.add(new FriendItem(uid, uname, nick, avatar));
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Parse friend list failed: {}", e.getMessage());
            }
        }).start();
    }

    public ObservableList<FriendItem> searchUsers(String keyword) {
        ObservableList<FriendItem> results = FXCollections.observableArrayList();
        try {
            String url = Config.getHttpBaseUrl() + "/api/user/search?q=" +
                    URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String resp = AuthService.getInstance().httpGet(url);
            if (resp == null) return results;
            JsonObject json = gson.fromJson(resp, JsonObject.class);
            int code = json.has("code") ? json.get("code").getAsInt() : -1;
            if (code == 0 && json.has("data")) {
                JsonArray arr = json.getAsJsonArray("data");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    long uid = obj.has("userId") ? obj.get("userId").getAsLong() : 0;
                    String uname = obj.has("username") ? obj.get("username").getAsString() : "";
                    String nick = obj.has("nickname") ? obj.get("nickname").getAsString() : "";
                    String avatar = obj.has("avatarUrl") ? obj.get("avatarUrl").getAsString() : "";
                    results.add(new FriendItem(uid, uname, nick, avatar));
                }
            }
        } catch (Exception e) {
            log.error("Search users failed: {}", e.getMessage());
        }
        return results;
    }

    public boolean applyFriend(long targetUserId) {
        JsonObject body = new JsonObject();
        body.addProperty("targetUserId", targetUserId);
        String resp = AuthService.getInstance().httpPost(
                Config.getHttpBaseUrl() + "/api/friend/apply", body.toString());
        if (resp != null) {
            try {
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                int code = json.has("code") ? json.get("code").getAsInt() : -1;
                if (code == 0) {
                    log.info("Friend request sent: targetUserId={}", targetUserId);
                    return true;
                }
            } catch (Exception e) {
                log.error("Parse apply response failed: {}", e.getMessage());
            }
        }
        return false;
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
