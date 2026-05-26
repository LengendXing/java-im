package com.im.server.gateway;

import com.im.server.common.*;
import com.im.protocol.ImProto;
import com.im.server.registry.NacosRegistryHolder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GatewayVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(GatewayVerticle.class);

    private final ConnectionManager connManager = new ConnectionManager();
    private final Map<String, Buffer> pendingBuffers = new ConcurrentHashMap<>();
    private final Map<String, Long> heartbeatTimes = new ConcurrentHashMap<>();
    private ServerConfig serverConfig;
    private String gatewayId;

    @Override
    public void stop() {
        log.info("GatewayVerticle stopping, draining connections for gatewayId={}", gatewayId);

        // Unregister all online users from Redis route table
        for (Connection conn : connManager.getAllConnections()) {
            if (conn.isAuthenticated()) {
                RedisServiceHolder.getInstance().setUserOffline(conn.getUserId())
                        .onFailure(err -> log.warn("route remove failed for user {}: {}", conn.getUserId(), err.getMessage()));
                conn.close();
            }
        }

        // Deregister from Nacos if enabled
        var nacos = NacosRegistryHolder.getInstance();
        if (nacos != null && nacos.isEnabled()) {
            nacos.deregister(
                    "0.0.0.0",
                    serverConfig != null ? serverConfig.getHttpPort() : 8080,
                    "im"
            );
        }

        log.info("GatewayVerticle {} stopped, all connections drained", gatewayId);
    }

    @Override
    public void start() {
        JsonObject cfg = config();
        serverConfig = ServerConfig.fromJson(cfg);

        int tcpPort = serverConfig.getTcpPort();
        int wsPort = serverConfig.getWsPort();
        long hbInterval = serverConfig.getHeartbeatInterval();
        long hbTimeout = serverConfig.getHeartbeatTimeout();

        // Generate unique gateway ID for routing
        gatewayId = "gateway-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        log.info("Gateway instance ID: {}", gatewayId);

        // TCP server
        NetServer tcpServer = vertx.createNetServer();
        tcpServer.connectHandler(this::handleConnection);
        tcpServer.listen(tcpPort, res -> {
            if (res.succeeded()) {
                log.info("Gateway TCP listening on port {}", tcpPort);
            } else {
                log.error("Gateway TCP listen failed: {}", res.cause().getMessage());
            }
        });

        // WebSocket server with CORS
        HttpServer wsServer = vertx.createHttpServer()
                .webSocketHandler(this::handleWebSocket);

        wsServer.listen(wsPort, res -> {
            if (res.succeeded()) {
                log.info("Gateway WS listening on port {}", wsPort);
            } else {
                log.error("Gateway WS listen failed: {}", res.cause().getMessage());
            }
        });

        // Subscribe to push messages addressed to this gateway instance
        vertx.eventBus().consumer("im.gateway." + gatewayId, msg -> {
            handlePushMessage(msg);
        });

        // Also subscribe to broadcast address for fallback compatibility
        vertx.eventBus().consumer("im.gateway.push", msg -> {
            handlePushMessage(msg);
        });

        // Periodic heartbeat check
        vertx.setPeriodic(hbInterval, id -> checkHeartbeats(hbTimeout));
    }

    public String getGatewayId() {
        return gatewayId;
    }

    private void handleConnection(NetSocket socket) {
        Connection conn = new Connection(socket);
        connManager.add(conn);
        heartbeatTimes.put(conn.getConnectionId(), System.currentTimeMillis());
        log.info("new TCP connection: {} from {}", conn.getConnectionId(), socket.remoteAddress());

        socket.handler(buf -> handleData(conn, buf));
        socket.closeHandler(v -> handleDisconnect(conn));
        socket.exceptionHandler(e -> log.error("connection {} error: {}", conn.getConnectionId(), e.getMessage()));
    }

    private void handleWebSocket(ServerWebSocket ws) {
        String wsConnId = "ws-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        Connection conn = new Connection(ws, wsConnId);
        connManager.add(conn);
        heartbeatTimes.put(wsConnId, System.currentTimeMillis());
        log.info("new WS connection: {}", wsConnId);

        ws.binaryMessageHandler(buf -> handleData(conn, buf));
        ws.closeHandler(v -> handleDisconnect(conn));
        ws.exceptionHandler(e -> log.error("WS {} error: {}", wsConnId, e.getMessage()));
    }

    private void handleData(Connection conn, Buffer buf) {
        String connId = conn.getConnectionId();
        Buffer pending = pendingBuffers.get(connId);
        if (pending != null) {
            buf = Buffer.buffer().appendBuffer(pending).appendBuffer(buf);
        }

        while (buf.length() >= Cmd.HEADER_LEN) {
            int dataLength = buf.getInt(12);
            int totalLen = Cmd.HEADER_LEN + dataLength;
            if (buf.length() < totalLen) break;

            Buffer frame = buf.slice(0, totalLen);
            buf = buf.slice(totalLen, buf.length());

            ImPacket packet = PacketCodec.decode(frame);
            if (packet != null) {
                routePacket(conn, packet);
            }
        }

        if (buf.length() > 0) {
            pendingBuffers.put(connId, buf);
        } else {
            pendingBuffers.remove(connId);
        }
    }

    private void routePacket(Connection conn, ImPacket packet) {
        switch (packet.getCmd()) {
            case Cmd.AUTH -> handleAuth(conn, packet);
            case Cmd.HEARTBEAT -> handleHeartbeat(conn, packet);
            case Cmd.C2C_MSG -> forwardToLogic("im.logic.C2C_MSG", conn, packet);
            case Cmd.GROUP_MSG -> forwardToLogic("im.logic.GROUP_MSG", conn, packet);
            case Cmd.MSG_ACK -> forwardToLogic("im.logic.SESSION", conn, packet);
            case Cmd.SYNC -> forwardToLogic("im.logic.SYNC", conn, packet);
            case Cmd.SESSION_LIST -> forwardToLogic("im.logic.SESSION", conn, packet);
            case Cmd.KEY_BUNDLE_REQUEST -> forwardToLogic("im.logic.KEY_BUNDLE_REQUEST", conn, packet);
            case Cmd.PUSH_TOKEN_REGISTER -> forwardToLogic("im.logic.PUSH_TOKEN_REGISTER", conn, packet);
            default -> log.warn("unknown cmd: 0x{}", Integer.toHexString(packet.getCmd()));
        }
    }

    private void handleAuth(Connection conn, ImPacket packet) {
        try {
            ImProto.AuthRequest req = ImProto.AuthRequest.parseFrom(packet.getBodyBytes());
            vertx.eventBus().<byte[]>request("im.logic.AUTH", req.toByteArray(), reply -> {
                try {
                    if (reply.succeeded() && reply.result().body() != null) {
                        ImProto.AuthResponse resp = ImProto.AuthResponse.parseFrom(reply.result().body());
                        if (resp.getCode() == 0) {
                            conn.setAuthInfo(resp.getUserInfo().getUserId(), req.getDeviceId(), req.getPlatform());
                            connManager.registerUser(conn);
                            heartbeatTimes.put(conn.getConnectionId(), System.currentTimeMillis());

                            // Register route in Redis pointing to this gateway
                            RedisServiceHolder.getInstance().setUserOnline(resp.getUserInfo().getUserId(), gatewayId, 3600)
                                    .onFailure(err -> log.warn("route set failed: {}", err.getMessage()));

                            log.info("user {} authenticated on {}", conn.getUserId(), conn.getConnectionId());
                        }
                        sendToConnection(conn, new ImPacket(Cmd.AUTH_ACK, MsgType.RESPONSE, packet.getSequenceId(), resp));
                    } else {
                        ImProto.AuthResponse err = ImProto.AuthResponse.newBuilder()
                                .setCode(1).setMsg("auth request failed").build();
                        sendToConnection(conn, new ImPacket(Cmd.AUTH_ACK, MsgType.RESPONSE, packet.getSequenceId(), err));
                    }
                } catch (Exception ex) {
                    log.error("auth response parse error: {}", ex.getMessage());
                    ImProto.AuthResponse err = ImProto.AuthResponse.newBuilder()
                            .setCode(1).setMsg("auth parse error").build();
                    sendToConnection(conn, new ImPacket(Cmd.AUTH_ACK, MsgType.RESPONSE, packet.getSequenceId(), err));
                }
            });
        } catch (Exception e) {
            log.error("auth parse error: {}", e.getMessage());
        }
    }

    private void handleHeartbeat(Connection conn, ImPacket packet) {
        heartbeatTimes.put(conn.getConnectionId(), System.currentTimeMillis());
        ImProto.HeartbeatResponse resp = ImProto.HeartbeatResponse.newBuilder()
                .setServerTime(System.currentTimeMillis()).build();
        sendToConnection(conn, new ImPacket(Cmd.HEARTBEAT_ACK, MsgType.RESPONSE, packet.getSequenceId(), resp));

        // Renew online status TTL
        if (conn.isAuthenticated()) {
            RedisServiceHolder.getInstance().setUserOnline(conn.getUserId(), gatewayId, 3600)
                    .onFailure(err -> log.warn("heartbeat route renew failed: {}", err.getMessage()));
        }
    }

    private void forwardToLogic(String address, Connection conn, ImPacket packet) {
        if (!conn.isAuthenticated()) {
            log.warn("unauthenticated user tried cmd 0x{}", Integer.toHexString(packet.getCmd()));
            return;
        }
        RequestEnvelope envelope = new RequestEnvelope(conn.getUserId(), packet.getCmd(), packet.getBodyBytes());
        byte[] envelopeBytes = envelope.encode();

        vertx.eventBus().<byte[]>request(address, envelopeBytes, reply -> {
            if (reply.succeeded() && reply.result().body() != null) {
                byte[] ackBytes = reply.result().body();
                try {
                    int ackCmd = getAckCmd(packet.getCmd());
                    if (ackCmd != 0) {
                        ImPacket ackPacket = new ImPacket(ackCmd, MsgType.RESPONSE, packet.getSequenceId(), ackBytes);
                        sendToConnection(conn, ackPacket);
                    }
                } catch (Exception e) {
                    log.error("ack send error: {}", e.getMessage());
                }
            }
        });
    }

    private int getAckCmd(int reqCmd) {
        return switch (reqCmd) {
            case Cmd.C2C_MSG -> Cmd.C2C_MSG_ACK;
            case Cmd.GROUP_MSG -> Cmd.GROUP_MSG_ACK;
            case Cmd.SESSION_LIST -> Cmd.SESSION_LIST_ACK;
            case Cmd.SYNC -> Cmd.SYNC_ACK;
            case Cmd.KEY_BUNDLE_REQUEST -> Cmd.KEY_BUNDLE_RESPONSE;
            case Cmd.PUSH_TOKEN_REGISTER -> Cmd.PUSH_TOKEN_REGISTER_ACK;
            case Cmd.MSG_ACK -> 0;
            default -> 0;
        };
    }

    private void handlePushMessage(io.vertx.core.eventbus.Message<Object> msg) {
        try {
            byte[] data = (byte[]) msg.body();
            Buffer buf = Buffer.buffer(data);
            long userId = buf.getLong(0);
            byte[] packetBytes = buf.getBytes(8, buf.length());

            Connection conn = connManager.getByUserId(userId);
            if (conn != null) {
                conn.write(Buffer.buffer(packetBytes));
                log.debug("push delivered to userId={}", userId);
            } else {
                log.debug("push target userId={} not on this gateway", userId);
            }
        } catch (Exception e) {
            log.error("push message handling error: {}", e.getMessage());
        }
    }

    private void sendToConnection(Connection conn, ImPacket packet) {
        try {
            conn.write(packet.encode());
        } catch (Exception ignored) {}
    }

    private void handleDisconnect(Connection conn) {
        connManager.remove(conn);
        pendingBuffers.remove(conn.getConnectionId());
        heartbeatTimes.remove(conn.getConnectionId());
        if (conn.isAuthenticated()) {
            // Remove route from Redis
            RedisServiceHolder.getInstance().setUserOffline(conn.getUserId())
                    .onFailure(err -> log.warn("route remove failed: {}", err.getMessage()));
            log.info("user {} disconnected", conn.getUserId());
        }
    }

    private void checkHeartbeats(long timeout) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : heartbeatTimes.entrySet()) {
            if (now - entry.getValue() > timeout) {
                Connection conn = connManager.getByConnId(entry.getKey());
                if (conn != null) {
                    log.warn("heartbeat timeout for user {} on {}", conn.getUserId(), entry.getKey());
                    conn.close();
                    handleDisconnect(conn);
                }
            }
        }
    }
}
