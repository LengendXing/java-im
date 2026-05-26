package com.im.client.network;

import com.im.client.util.Config;
import com.im.client.util.JwtUtil;
import com.im.protocol.ImProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpConnection {

    private static final Logger log = LoggerFactory.getLogger(TcpConnection.class);
    private static TcpConnection instance;

    private Socket socket;
    private DataOutputStream outputStream;
    private DataInputStream inputStream;
    private final AtomicInteger sequenceId = new AtomicInteger(0);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService readExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private ResponseHandler responseHandler;

    private TcpConnection() {
    }

    public static synchronized TcpConnection getInstance() {
        if (instance == null) {
            instance = new TcpConnection();
        }
        return instance;
    }

    public void setResponseHandler(ResponseHandler handler) {
        this.responseHandler = handler;
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void connect() {
        if (connected.get()) {
            return;
        }
        running.set(true);
        doConnect();
    }

    private void doConnect() {
        try {
            socket = new Socket(Config.SERVER_HOST, Config.SERVER_TCP_PORT);
            socket.setTcpNoDelay(true);
            outputStream = new DataOutputStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());
            connected.set(true);
            log.info("Connected to server {}:{}", Config.SERVER_HOST, Config.SERVER_TCP_PORT);

            readExecutor.submit(this::readLoop);
            startHeartbeat();
            sendAuth();
        } catch (IOException e) {
            log.error("Connect failed: {}", e.getMessage());
            connected.set(false);
            scheduleReconnect();
        }
    }

    private void sendAuth() {
        String token = JwtUtil.getToken();
        if (token == null || token.isEmpty()) {
            log.warn("No JWT token, skip auth");
            return;
        }
        ImProto.AuthRequest authReq = ImProto.AuthRequest.newBuilder()
                .setToken(token)
                .setDeviceId("pc-client")
                .setPlatform(3)
                .build();
        sendPacket(ImPacket.CMD_AUTH, ImPacket.MSG_TYPE_REQUEST, authReq.toByteArray());
        log.info("Auth request sent");
    }

    private void readLoop() {
        try {
            while (running.get() && connected.get()) {
                ImPacket packet = PacketCodec.decode(inputStream);
                log.debug("Received packet: {}", packet);
                if (responseHandler != null) {
                    responseHandler.handle(packet);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                log.error("Read error: {}", e.getMessage());
                connected.set(false);
                scheduleReconnect();
            }
        }
    }

    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (connected.get()) {
                try {
                    ImProto.HeartbeatRequest hb = ImProto.HeartbeatRequest.newBuilder().build();
                    sendPacket(ImPacket.CMD_HEARTBEAT, ImPacket.MSG_TYPE_REQUEST, hb.toByteArray());
                    log.debug("Heartbeat sent");
                } catch (Exception e) {
                    log.error("Heartbeat failed: {}", e.getMessage());
                }
            }
        }, Config.HEARTBEAT_INTERVAL_SEC, Config.HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private void scheduleReconnect() {
        heartbeatExecutor.schedule(() -> {
            if (running.get() && !connected.get()) {
                log.info("Reconnecting...");
                closeSocket();
                doConnect();
            }
        }, 5, TimeUnit.SECONDS);
    }

    public int sendPacket(short cmd, byte msgType, byte[] body) {
        if (!connected.get()) {
            log.warn("Not connected, cannot send cmd={}", cmd);
            return -1;
        }
        int seqId = sequenceId.incrementAndGet();
        ImPacket packet = new ImPacket(cmd, msgType, seqId, body);
        try {
            byte[] data = PacketCodec.encode(packet);
            synchronized (outputStream) {
                outputStream.write(data);
                outputStream.flush();
            }
        } catch (IOException e) {
            log.error("Send failed: {}", e.getMessage());
            connected.set(false);
            scheduleReconnect();
        }
        return seqId;
    }

    private void closeSocket() {
        try {
            if (inputStream != null) inputStream.close();
        } catch (IOException ignored) {
        }
        try {
            if (outputStream != null) outputStream.close();
        } catch (IOException ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }

    public void disconnect() {
        running.set(false);
        connected.set(false);
        heartbeatExecutor.shutdownNow();
        readExecutor.shutdownNow();
        closeSocket();
        log.info("Disconnected");
    }
}
