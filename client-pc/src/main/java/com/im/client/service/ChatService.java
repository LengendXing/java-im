package com.im.client.service;

import com.im.client.model.MessageModel;
import com.im.client.model.SessionModel;
import com.im.client.network.ImPacket;
import com.im.client.network.TcpConnection;
import com.im.protocol.ImProto;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static ChatService instance;

    private ChatService() {
    }

    public static synchronized ChatService getInstance() {
        if (instance == null) {
            instance = new ChatService();
        }
        return instance;
    }

    public String sendC2CMessage(long receiverId, String text) {
        return sendC2CMessage(receiverId, 1, text, null);
    }

    public String sendC2CMessage(long receiverId, int msgType, String text, String url) {
        String clientMsgId = UUID.randomUUID().toString();
        ImProto.MessageContent.Builder contentBuilder = ImProto.MessageContent.newBuilder()
                .setMsgType(msgType)
                .setText(text != null ? text : "");
        if (url != null) {
            contentBuilder.setUrl(url);
        }
        ImProto.MessageContent content = contentBuilder.build();

        ImProto.C2CMsgRequest req = ImProto.C2CMsgRequest.newBuilder()
                .setReceiverId(receiverId)
                .setContent(content)
                .setClientMsgId(clientMsgId)
                .build();

        TcpConnection.getInstance().sendPacket(ImPacket.CMD_C2C_MSG, ImPacket.MSG_TYPE_REQUEST, req.toByteArray());
        return clientMsgId;
    }

    public String sendGroupMessage(long groupId, String text) {
        return sendGroupMessage(groupId, 1, text, null);
    }

    public String sendGroupMessage(long groupId, int msgType, String text, String url) {
        String clientMsgId = UUID.randomUUID().toString();
        ImProto.MessageContent.Builder contentBuilder = ImProto.MessageContent.newBuilder()
                .setMsgType(msgType)
                .setText(text != null ? text : "");
        if (url != null) {
            contentBuilder.setUrl(url);
        }
        ImProto.MessageContent content = contentBuilder.build();

        ImProto.GroupMsgRequest req = ImProto.GroupMsgRequest.newBuilder()
                .setGroupId(groupId)
                .setContent(content)
                .setClientMsgId(clientMsgId)
                .build();

        TcpConnection.getInstance().sendPacket(ImPacket.CMD_GROUP_MSG, ImPacket.MSG_TYPE_REQUEST, req.toByteArray());
        return clientMsgId;
    }

    public void onC2CMsgAck(ImProto.C2CMsgResponse resp) {
        if (resp.getCode() == 0) {
            log.info("C2C msg ack: msgId={}, seq={}", resp.getMsgId(), resp.getSeq());
        } else {
            log.error("C2C msg send failed: {}", resp.getMsg());
        }
    }

    public void onGroupMsgAck(ImProto.GroupMsgResponse resp) {
        if (resp.getCode() == 0) {
            log.info("Group msg ack: msgId={}, seq={}", resp.getMsgId(), resp.getSeq());
        } else {
            log.error("Group msg send failed: {}", resp.getMsg());
        }
    }

    public void onC2CMsgNotify(ImProto.C2CMsgNotify notify) {
        log.info("C2C msg notify: from={}, text={}", notify.getSenderId(),
                notify.getContent().getText());

        MessageModel msg = new MessageModel();
        msg.setMsgId(notify.getMsgId());
        msg.setSenderId(notify.getSenderId());
        msg.setSessionId(notify.getSessionId());
        msg.setText(notify.getContent().getText());
        msg.setMsgType(notify.getContent().getMsgType());
        if (!notify.getContent().getUrl().isEmpty()) {
            msg.setUrl(notify.getContent().getUrl());
        }
        if (!notify.getContent().getFileName().isEmpty()) {
            msg.setFileName(notify.getContent().getFileName());
        }
        if (notify.getContent().getFileSize() > 0) {
            msg.setFileSize(notify.getContent().getFileSize());
        }
        msg.setServerTime(notify.getServerTime());
        msg.setSentByMe(false);

        SessionService sessionService = SessionService.getInstance();
        Platform.runLater(() -> {
            sessionService.addMessageToSession(notify.getSessionId(), msg);
            sessionService.updateSessionLastMsg(notify.getSessionId(),
                    notify.getContent().getText(), notify.getServerTime());
            sessionService.incrementUnread(notify.getSessionId());
        });

        sendMsgAck(notify.getMsgId(), notify.getSessionId(), notify.getSeq());
    }

    public void onGroupMsgNotify(ImProto.GroupMsgNotify notify) {
        log.info("Group msg notify: from={}, group={}, text={}", notify.getSenderId(),
                notify.getGroupId(), notify.getContent().getText());

        MessageModel msg = new MessageModel();
        msg.setMsgId(notify.getMsgId());
        msg.setSenderId(notify.getSenderId());
        msg.setSessionId(notify.getSessionId());
        msg.setText(notify.getContent().getText());
        msg.setMsgType(notify.getContent().getMsgType());
        if (!notify.getContent().getUrl().isEmpty()) {
            msg.setUrl(notify.getContent().getUrl());
        }
        if (!notify.getContent().getFileName().isEmpty()) {
            msg.setFileName(notify.getContent().getFileName());
        }
        if (notify.getContent().getFileSize() > 0) {
            msg.setFileSize(notify.getContent().getFileSize());
        }
        msg.setServerTime(notify.getServerTime());
        msg.setSentByMe(false);

        SessionService sessionService = SessionService.getInstance();
        Platform.runLater(() -> {
            sessionService.addMessageToSession(notify.getSessionId(), msg);
            sessionService.updateSessionLastMsg(notify.getSessionId(),
                    notify.getContent().getText(), notify.getServerTime());
            sessionService.incrementUnread(notify.getSessionId());
        });

        sendMsgAck(notify.getMsgId(), notify.getSessionId(), notify.getSeq());
    }

    private void sendMsgAck(long msgId, String sessionId, long seq) {
        ImProto.MsgAckRequest ack = ImProto.MsgAckRequest.newBuilder()
                .setMsgId(msgId)
                .setSessionId(sessionId)
                .setSeq(seq)
                .build();
        TcpConnection.getInstance().sendPacket(ImPacket.CMD_MSG_ACK, ImPacket.MSG_TYPE_REQUEST, ack.toByteArray());
    }
}
