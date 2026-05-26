package com.im.server.storage;

public class Message {
    private long msgId;
    private String sessionId;
    private long senderId;
    private long seq;
    private int contentType;
    private String contentText;
    private String contentUrl;
    private String contentExtra;
    private String clientMsgId;
    private long serverTime;

    public Message() {}

    public long getMsgId() { return msgId; }
    public void setMsgId(long msgId) { this.msgId = msgId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public long getSenderId() { return senderId; }
    public void setSenderId(long senderId) { this.senderId = senderId; }
    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
    public int getContentType() { return contentType; }
    public void setContentType(int contentType) { this.contentType = contentType; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public String getContentUrl() { return contentUrl; }
    public void setContentUrl(String contentUrl) { this.contentUrl = contentUrl; }
    public String getContentExtra() { return contentExtra; }
    public void setContentExtra(String contentExtra) { this.contentExtra = contentExtra; }
    public String getClientMsgId() { return clientMsgId; }
    public void setClientMsgId(String clientMsgId) { this.clientMsgId = clientMsgId; }
    public long getServerTime() { return serverTime; }
    public void setServerTime(long serverTime) { this.serverTime = serverTime; }
}
