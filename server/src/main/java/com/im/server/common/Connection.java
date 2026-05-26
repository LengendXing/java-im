package com.im.server.common;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.NetSocket;

import java.util.concurrent.atomic.AtomicInteger;

public class Connection {
    private final String connectionId;
    private final NetSocket tcpSocket;
    private final ServerWebSocket wsSocket;
    private final boolean isWebSocket;
    private final long connectedAt;
    private volatile long userId;
    private volatile String deviceId;
    private volatile int platform;
    private final AtomicInteger sequenceId = new AtomicInteger(0);

    // TCP connection constructor
    public Connection(NetSocket socket) {
        this.connectionId = java.util.UUID.randomUUID().toString().substring(0, 8);
        this.tcpSocket = socket;
        this.wsSocket = null;
        this.isWebSocket = false;
        this.connectedAt = System.currentTimeMillis();
    }

    // WebSocket connection constructor
    public Connection(ServerWebSocket ws, String connId) {
        this.connectionId = connId;
        this.tcpSocket = null;
        this.wsSocket = ws;
        this.isWebSocket = true;
        this.connectedAt = System.currentTimeMillis();
    }

    public String getConnectionId() { return connectionId; }
    public NetSocket getSocket() { return tcpSocket; }
    public long getConnectedAt() { return connectedAt; }
    public long getUserId() { return userId; }
    public String getDeviceId() { return deviceId; }
    public int getPlatform() { return platform; }
    public boolean isAuthenticated() { return userId > 0; }
    public boolean isWebSocket() { return isWebSocket; }

    public void setAuthInfo(long userId, String deviceId, int platform) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.platform = platform;
    }

    public int nextSequenceId() {
        return sequenceId.incrementAndGet();
    }

    public void write(Buffer data) {
        if (isWebSocket && wsSocket != null) {
            wsSocket.writeBinaryMessage(data);
        } else if (tcpSocket != null) {
            tcpSocket.write(data);
        }
    }

    public void close() {
        if (isWebSocket && wsSocket != null) {
            wsSocket.close();
        } else if (tcpSocket != null) {
            tcpSocket.close();
        }
    }
}
