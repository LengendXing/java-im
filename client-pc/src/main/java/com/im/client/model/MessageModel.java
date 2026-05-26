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
    private final IntegerProperty msgType = new SimpleIntegerProperty(1);
    private final StringProperty url = new SimpleStringProperty();
    private final StringProperty fileName = new SimpleStringProperty();
    private final LongProperty fileSize = new SimpleLongProperty();
    private final BooleanProperty recalled = new SimpleBooleanProperty();

    public static final int MSG_TYPE_TEXT = 1;
    public static final int MSG_TYPE_IMAGE = 2;
    public static final int MSG_TYPE_FILE = 3;

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

    public int getMsgType() { return msgType.get(); }
    public void setMsgType(int value) { msgType.set(value); }
    public IntegerProperty msgTypeProperty() { return msgType; }

    public String getUrl() { return url.get(); }
    public void setUrl(String value) { url.set(value); }
    public StringProperty urlProperty() { return url; }

    public String getFileName() { return fileName.get(); }
    public void setFileName(String value) { fileName.set(value); }
    public StringProperty fileNameProperty() { return fileName; }

    public long getFileSize() { return fileSize.get(); }
    public void setFileSize(long value) { fileSize.set(value); }
    public LongProperty fileSizeProperty() { return fileSize; }

    public boolean isRecalled() { return recalled.get(); }
    public void setRecalled(boolean value) { recalled.set(value); }
    public BooleanProperty recalledProperty() { return recalled; }
}
