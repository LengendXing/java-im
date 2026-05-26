package com.im.client.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class SessionModel {

    private final StringProperty sessionId = new SimpleStringProperty();
    private final IntegerProperty type = new SimpleIntegerProperty();
    private final LongProperty targetId = new SimpleLongProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty avatarUrl = new SimpleStringProperty();
    private final StringProperty lastMsg = new SimpleStringProperty();
    private final LongProperty lastMsgTime = new SimpleLongProperty();
    private final IntegerProperty unreadCount = new SimpleIntegerProperty();
    private final BooleanProperty isTop = new SimpleBooleanProperty();
    private final BooleanProperty isMuted = new SimpleBooleanProperty();
    private final ObservableList<MessageModel> messages = FXCollections.observableArrayList();

    public SessionModel() {
    }

    public String getSessionId() { return sessionId.get(); }
    public void setSessionId(String value) { sessionId.set(value); }
    public StringProperty sessionIdProperty() { return sessionId; }

    public int getType() { return type.get(); }
    public void setType(int value) { type.set(value); }
    public IntegerProperty typeProperty() { return type; }

    public long getTargetId() { return targetId.get(); }
    public void setTargetId(long value) { targetId.set(value); }
    public LongProperty targetIdProperty() { return targetId; }

    public String getName() { return name.get(); }
    public void setName(String value) { name.set(value); }
    public StringProperty nameProperty() { return name; }

    public String getAvatarUrl() { return avatarUrl.get(); }
    public void setAvatarUrl(String value) { avatarUrl.set(value); }
    public StringProperty avatarUrlProperty() { return avatarUrl; }

    public String getLastMsg() { return lastMsg.get(); }
    public void setLastMsg(String value) { lastMsg.set(value); }
    public StringProperty lastMsgProperty() { return lastMsg; }

    public long getLastMsgTime() { return lastMsgTime.get(); }
    public void setLastMsgTime(long value) { lastMsgTime.set(value); }
    public LongProperty lastMsgTimeProperty() { return lastMsgTime; }

    public int getUnreadCount() { return unreadCount.get(); }
    public void setUnreadCount(int value) { unreadCount.set(value); }
    public IntegerProperty unreadCountProperty() { return unreadCount; }

    public boolean isIsTop() { return isTop.get(); }
    public void setIsTop(boolean value) { isTop.set(value); }
    public BooleanProperty isTopProperty() { return isTop; }

    public boolean isIsMuted() { return isMuted.get(); }
    public void setIsMuted(boolean value) { isMuted.set(value); }
    public BooleanProperty isMutedProperty() { return isMuted; }

    public ObservableList<MessageModel> getMessages() { return messages; }
}
