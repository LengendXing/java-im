package com.im.client.network;

import com.im.client.service.ChatService;
import com.im.client.service.SessionService;
import com.im.protocol.ImProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ResponseHandler.class);

    public void handle(ImPacket packet) {
        try {
            switch (packet.getCmd()) {
                case ImPacket.CMD_AUTH -> handleAuth(packet);
                case ImPacket.CMD_HEARTBEAT -> handleHeartbeat(packet);
                case ImPacket.CMD_C2C_MSG -> handleC2CMsgResponse(packet);
                case ImPacket.CMD_GROUP_MSG -> handleGroupMsgResponse(packet);
                case ImPacket.CMD_C2C_MSG_NOTIFY -> handleC2CMsgNotify(packet);
                case ImPacket.CMD_GROUP_MSG_NOTIFY -> handleGroupMsgNotify(packet);
                case ImPacket.CMD_SESSION_LIST -> handleSessionList(packet);
                case ImPacket.CMD_SYNC -> handleSync(packet);
                default -> log.warn("Unhandled cmd: {}", packet.getCmd());
            }
        } catch (Exception e) {
            log.error("Error handling packet cmd={}: {}", packet.getCmd(), e.getMessage());
        }
    }

    private void handleAuth(ImPacket packet) {
        try {
            ImProto.AuthResponse resp = ImProto.AuthResponse.parseFrom(packet.getBody());
            if (resp.getCode() == 0) {
                log.info("Auth success: userId={}", resp.getUserInfo().getUserId());
                SessionService.getInstance().requestSessionList();
            } else {
                log.error("Auth failed: {}", resp.getMsg());
            }
        } catch (Exception e) {
            log.error("Parse auth response failed: {}", e.getMessage());
        }
    }

    private void handleHeartbeat(ImPacket packet) {
        log.debug("Heartbeat response received");
    }

    private void handleC2CMsgResponse(ImPacket packet) {
        try {
            ImProto.C2CMsgResponse resp = ImProto.C2CMsgResponse.parseFrom(packet.getBody());
            ChatService.getInstance().onC2CMsgAck(resp);
        } catch (Exception e) {
            log.error("Parse C2C msg response failed: {}", e.getMessage());
        }
    }

    private void handleGroupMsgResponse(ImPacket packet) {
        try {
            ImProto.GroupMsgResponse resp = ImProto.GroupMsgResponse.parseFrom(packet.getBody());
            ChatService.getInstance().onGroupMsgAck(resp);
        } catch (Exception e) {
            log.error("Parse group msg response failed: {}", e.getMessage());
        }
    }

    private void handleC2CMsgNotify(ImPacket packet) {
        try {
            ImProto.C2CMsgNotify notify = ImProto.C2CMsgNotify.parseFrom(packet.getBody());
            ChatService.getInstance().onC2CMsgNotify(notify);
        } catch (Exception e) {
            log.error("Parse C2C msg notify failed: {}", e.getMessage());
        }
    }

    private void handleGroupMsgNotify(ImPacket packet) {
        try {
            ImProto.GroupMsgNotify notify = ImProto.GroupMsgNotify.parseFrom(packet.getBody());
            ChatService.getInstance().onGroupMsgNotify(notify);
        } catch (Exception e) {
            log.error("Parse group msg notify failed: {}", e.getMessage());
        }
    }

    private void handleSessionList(ImPacket packet) {
        try {
            ImProto.SessionListResponse resp = ImProto.SessionListResponse.parseFrom(packet.getBody());
            SessionService.getInstance().onSessionList(resp);
        } catch (Exception e) {
            log.error("Parse session list failed: {}", e.getMessage());
        }
    }

    private void handleSync(ImPacket packet) {
        try {
            ImProto.SyncResponse resp = ImProto.SyncResponse.parseFrom(packet.getBody());
            SessionService.getInstance().onSyncResponse(resp);
        } catch (Exception e) {
            log.error("Parse sync response failed: {}", e.getMessage());
        }
    }
}
