package com.im.client.model;

import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class UserModel {

    private final LongProperty userId = new SimpleLongProperty();
    private final StringProperty username = new SimpleStringProperty();
    private final StringProperty nickname = new SimpleStringProperty();
    private final StringProperty avatarUrl = new SimpleStringProperty();

    public UserModel() {
    }

    public UserModel(long userId, String username, String nickname, String avatarUrl) {
        this.userId.set(userId);
        this.username.set(username);
        this.nickname.set(nickname);
        this.avatarUrl.set(avatarUrl);
    }

    public long getUserId() { return userId.get(); }
    public void setUserId(long value) { userId.set(value); }
    public LongProperty userIdProperty() { return userId; }

    public String getUsername() { return username.get(); }
    public void setUsername(String value) { username.set(value); }
    public StringProperty usernameProperty() { return username; }

    public String getNickname() { return nickname.get(); }
    public void setNickname(String value) { nickname.set(value); }
    public StringProperty nicknameProperty() { return nickname; }

    public String getAvatarUrl() { return avatarUrl.get(); }
    public void setAvatarUrl(String value) { avatarUrl.set(value); }
    public StringProperty avatarUrlProperty() { return avatarUrl; }
}
