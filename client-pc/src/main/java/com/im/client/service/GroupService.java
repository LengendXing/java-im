package com.im.client.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.im.client.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for group management: create, invite, kick, dissolve.
 */
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);
    private static GroupService instance;
    private final Gson gson = new Gson();

    private GroupService() {}

    public static synchronized GroupService getInstance() {
        if (instance == null) {
            instance = new GroupService();
        }
        return instance;
    }

    /**
     * Create a group.
     * @param name group name
     * @param memberIds array of member user IDs
     * @return groupId on success, -1 on failure
     */
    public long createGroup(String name, long[] memberIds) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        JsonArray arr = new JsonArray();
        for (long id : memberIds) {
            arr.add(id);
        }
        body.add("memberIds", arr);

        String resp = AuthService.getInstance().httpPost(
                Config.getHttpBaseUrl() + "/api/group/create", body.toString());
        if (resp != null) {
            try {
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                int code = json.has("code") ? json.get("code").getAsInt() : -1;
                if (code == 0 && json.has("data")) {
                    long groupId = json.getAsJsonObject("data").get("groupId").getAsLong();
                    log.info("Group created: groupId={}, name={}", groupId, name);
                    return groupId;
                }
            } catch (Exception e) {
                log.error("Parse create group response failed: {}", e.getMessage());
            }
        }
        return -1;
    }

    /**
     * Invite users to a group.
     * @return true on success
     */
    public boolean invite(long groupId, long[] userIds) {
        JsonObject body = new JsonObject();
        body.addProperty("groupId", groupId);
        JsonArray arr = new JsonArray();
        for (long id : userIds) {
            arr.add(id);
        }
        body.add("userIds", arr);

        String resp = AuthService.getInstance().httpPost(
                Config.getHttpBaseUrl() + "/api/group/invite", body.toString());
        return isResponseOk(resp, "invite");
    }

    /**
     * Kick a user from a group (owner only).
     * @return true on success
     */
    public boolean kick(long groupId, long userId) {
        JsonObject body = new JsonObject();
        body.addProperty("groupId", groupId);
        body.addProperty("userId", userId);

        String resp = AuthService.getInstance().httpPost(
                Config.getHttpBaseUrl() + "/api/group/kick", body.toString());
        return isResponseOk(resp, "kick");
    }

    /**
     * Dissolve a group (owner only).
     * @return true on success
     */
    public boolean dissolve(long groupId) {
        JsonObject body = new JsonObject();
        body.addProperty("groupId", groupId);

        String resp = AuthService.getInstance().httpPost(
                Config.getHttpBaseUrl() + "/api/group/dissolve", body.toString());
        return isResponseOk(resp, "dissolve");
    }

    private boolean isResponseOk(String resp, String action) {
        if (resp != null) {
            try {
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                int code = json.has("code") ? json.get("code").getAsInt() : -1;
                if (code == 0) {
                    log.info("Group {} success", action);
                    return true;
                }
                log.warn("Group {} failed: code={}", action, code);
            } catch (Exception e) {
                log.error("Parse group {} response failed: {}", action, e.getMessage());
            }
        }
        return false;
    }
}
