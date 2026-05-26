package com.im.server.common;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {
    private final ConcurrentHashMap<String, Connection> byConnId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Connection> byUserId = new ConcurrentHashMap<>();

    public void add(Connection conn) {
        byConnId.put(conn.getConnectionId(), conn);
    }

    public void registerUser(Connection conn) {
        byUserId.put(conn.getUserId(), conn);
    }

    public void remove(Connection conn) {
        byConnId.remove(conn.getConnectionId());
        if (conn.isAuthenticated()) {
            byUserId.remove(conn.getUserId(), conn);
        }
    }

    public Connection getByUserId(long userId) {
        return byUserId.get(userId);
    }

    public Connection getByConnId(String connId) {
        return byConnId.get(connId);
    }

    public int onlineCount() {
        return byUserId.size();
    }

    public int connectionCount() {
        return byConnId.size();
    }

    public java.util.Collection<Connection> getAllConnections() {
        return byConnId.values();
    }
}
