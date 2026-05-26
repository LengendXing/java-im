package com.im.client.model;

import javafx.beans.property.*;

public class MessageModel {

    private final LongProperty msgId = new SimpleLongProperty();
    private final LongProperty senderId = new SimpleLongProperty();
    private final StringProperty sessionId = new SimpleStringProperty();
    private final StringProperty text = new SimpleStringProperty();
    private final LongProperty serverTime = new SimpleLongProperty();
    private final StringProperty clientMsgId = new SimpleStringProperty();
    private final BooleanProperty sentByMe = new SimpleBooleanProperty();

    public MessageModel() {
    }

    public long getMsgId() { return msgId.get(); }
    public void setMsgId(long value) { msgId.set(value); }
    public LongProperty msgIdProperty() { return msgId; }

    public long getSenderId() { return senderId.get(); }
    public void setSenderId(long value) { senderId.set(value); }
    public LongProperty senderIdProperty() { return senderId; }

    public String getSessionId() { return sessionId.get(); }
    public void setSessionId(String value) { sessionId.set(value); }
    public StringProperty sessionIdProperty() { return sessionId; }

    public String getText() { return text.get(); }
    public void setText(String value) { text.set(value); }
    public StringProperty textProperty() { return text; }

    public long getServerTime() { return serverTime.get(); }
    public void setServerTime(long value) { serverTime.set(value); }
    public LongProperty serverTimeProperty() { return serverTime; }

    public String getClientMsgId() { return clientMsgId.get(); }
    public void setClientMsgId(String value) { clientMsgId.set(value); }
    public StringProperty clientMsgIdProperty() { return clientMsgId; }

    public boolean isSentByMe() { return sentByMe.get(); }
    public void setSentByMe(boolean value) { sentByMe.set(value); }
    public BooleanProperty sentByMeProperty() { return sentByMe; }
}
