package com.im.client.service;

import com.im.client.model.MessageModel;
import com.im.client.model.SessionModel;
import com.im.client.network.ImPacket;
import com.im.client.network.TcpConnection;
import com.im.protocol.ImProto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static SessionService instance;

    private final ObservableList<SessionModel> sessions = FXCollections.observableArrayList();

    private SessionService() {
    }

    public static synchronized SessionService getInstance() {
        if (instance == null) {
            instance = new SessionService();
        }
        return instance;
    }

    public ObservableList<SessionModel> getSessions() {
        return sessions;
    }

    public void requestSessionList() {
        ImProto.SessionListRequest req = ImProto.SessionListRequest.newBuilder()
                .setLastUpdateTime(0)
                .setLimit(100)
                .build();
        TcpConnection.getInstance().sendPacket(ImPacket.CMD_SESSION_LIST,
                ImPacket.MSG_TYPE_REQUEST, req.toByteArray());
        log.info("Session list request sent");
    }

    public void onSessionList(ImProto.SessionListResponse resp) {
        Platform.runLater(() -> {
            sessions.clear();
            for (ImProto.SessionInfo info : resp.getSessionsList()) {
                SessionModel sm = new SessionModel();
                sm.setSessionId(info.getSessionId());
                sm.setType(info.getType());
                sm.setTargetId(info.getTargetId());
                sm.setName(info.getName());
                sm.setAvatarUrl(info.getAvatarUrl());
                sm.setLastMsg(info.getLastMsg());
                sm.setLastMsgTime(info.getLastMsgTime());
                sm.setUnreadCount(info.getUnreadCount());
                sm.setIsTop(info.getIsTop());
                sm.setIsMuted(info.getIsMuted());
                sessions.add(sm);
            }
            log.info("Session list updated: {} sessions", sessions.size());
        });
    }

    public void requestSync(String sessionId, long lastSeq) {
        ImProto.SyncPoint point = ImProto.SyncPoint.newBuilder()
                .setSessionId(sessionId)
                .setLastSeq(lastSeq)
                .build();
        ImProto.SyncRequest req = ImProto.SyncRequest.newBuilder()
                .addPoints(point)
                .setLimit(50)
                .build();
        TcpConnection.getInstance().sendPacket(ImPacket.CMD_SYNC,
                ImPacket.MSG_TYPE_REQUEST, req.toByteArray());
    }

    public void onSyncResponse(ImProto.SyncResponse resp) {
        Platform.runLater(() -> {
            for (ImProto.SessionMessages sm : resp.getSessionsList()) {
                SessionModel session = findSession(sm.getSessionId());
                if (session != null) {
                    for (ImProto.Message protoMsg : sm.getMessagesList()) {
                        MessageModel msg = new MessageModel();
                        msg.setMsgId(protoMsg.getMsgId());
                        msg.setSenderId(protoMsg.getSenderId());
                        msg.setSessionId(sm.getSessionId());
                        msg.setText(protoMsg.getContent().getText());
                        msg.setMsgType(protoMsg.getContent().getMsgType());
                        if (!protoMsg.getContent().getUrl().isEmpty()) {
                            msg.setUrl(protoMsg.getContent().getUrl());
                        }
                        if (!protoMsg.getContent().getFileName().isEmpty()) {
                            msg.setFileName(protoMsg.getContent().getFileName());
                        }
                        if (protoMsg.getContent().getFileSize() > 0) {
                            msg.setFileSize(protoMsg.getContent().getFileSize());
                        }
                        msg.setRecalled(protoMsg.getIsRecalled());
                        msg.setServerTime(protoMsg.getServerTime());
                        session.getMessages().add(msg);
                    }
                }
            }
        });
    }

    public void addMessageToSession(String sessionId, MessageModel msg) {
        SessionModel session = findSession(sessionId);
        if (session != null) {
            session.getMessages().add(msg);
        }
    }

    public void updateSessionLastMsg(String sessionId, String lastMsg, long lastMsgTime) {
        SessionModel session = findSession(sessionId);
        if (session != null) {
            session.setLastMsg(lastMsg);
            session.setLastMsgTime(lastMsgTime);
        }
    }

    public void incrementUnread(String sessionId) {
        SessionModel session = findSession(sessionId);
        if (session != null) {
            session.setUnreadCount(session.getUnreadCount() + 1);
        }
    }

    public void clearUnread(String sessionId) {
        SessionModel session = findSession(sessionId);
        if (session != null) {
            session.setUnreadCount(0);
        }
    }

    private SessionModel findSession(String sessionId) {
        for (SessionModel s : sessions) {
            if (s.getSessionId().equals(sessionId)) {
                return s;
            }
        }
        return null;
    }
}
