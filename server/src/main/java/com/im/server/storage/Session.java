package com.im.server.storage;

public class Session {
    private long id;
    private long userId;
    private String sessionId;
    private int type; // 1=c2c, 2=group
    private long targetId;
    private String name;
    private String avatarUrl;
    private String lastMsg;
    private long lastMsgTime;
    private int unreadCount;
    private boolean isTop;
    private boolean isMuted;
    private long updatedAt;

    public Session() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public long getTargetId() { return targetId; }
    public void setTargetId(long targetId) { this.targetId = targetId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getLastMsg() { return lastMsg; }
    public void setLastMsg(String lastMsg) { this.lastMsg = lastMsg; }
    public long getLastMsgTime() { return lastMsgTime; }
    public void setLastMsgTime(long lastMsgTime) { this.lastMsgTime = lastMsgTime; }
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
    public boolean isTop() { return isTop; }
    public void setTop(boolean top) { isTop = top; }
    public boolean isMuted() { return isMuted; }
    public void setMuted(boolean muted) { isMuted = muted; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
