package com.im.client.controller;

import com.im.client.model.MessageModel;
import com.im.client.model.SessionModel;
import com.im.client.service.ChatService;
import com.im.client.service.SessionService;
import com.im.client.util.JwtUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML private ListView<SessionModel> sessionListView;
    @FXML private Label chatTargetLabel;
    @FXML private ListView<MessageModel> messageListView;
    @FXML private TextArea messageInput;
    @FXML private Button sendButton;

    private SessionModel currentSession;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSessionList();
        setupMessageList();
        setupInput();

        SessionService.getInstance().getSessions().addListener(
                (javafx.collections.ListChangeListener<SessionModel>) change -> {
                    while (change.next()) {
                        if (change.wasUpdated()) {
                            sessionListView.refresh();
                        }
                    }
                });
    }

    private void setupSessionList() {
        sessionListView.setItems(SessionService.getInstance().getSessions());
        sessionListView.setCellFactory(list -> new SessionCell());
        sessionListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        onSessionSelected(newVal);
                    }
                });
    }

    private void setupMessageList() {
        messageListView.setCellFactory(list -> new MessageCell());
    }

    private void setupInput() {
        sendButton.setOnAction(e -> onSend());
        messageInput.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && !e.isShiftDown()) {
                onSend();
                e.consume();
            }
        });
    }

    private void onSessionSelected(SessionModel session) {
        currentSession = session;
        chatTargetLabel.setText(session.getName());
        messageListView.setItems(session.getMessages());
        SessionService.getInstance().clearUnread(session.getSessionId());

        if (session.getMessages().isEmpty()) {
            SessionService.getInstance().requestSync(session.getSessionId(), 0);
        }
        messageInput.setDisable(false);
        sendButton.setDisable(false);
    }

    private void onSend() {
        if (currentSession == null) return;
        String text = messageInput.getText().trim();
        if (text.isEmpty()) return;

        String clientMsgId;
        if (currentSession.getType() == 1) {
            clientMsgId = ChatService.getInstance().sendC2CMessage(currentSession.getTargetId(), text);
        } else {
            clientMsgId = ChatService.getInstance().sendGroupMessage(currentSession.getTargetId(), text);
        }

        MessageModel msg = new MessageModel();
        msg.setClientMsgId(clientMsgId);
        msg.setSessionId(currentSession.getSessionId());
        msg.setText(text);
        msg.setSentByMe(true);
        msg.setServerTime(System.currentTimeMillis());

        currentSession.getMessages().add(msg);
        messageInput.clear();

        SessionService.getInstance().updateSessionLastMsg(
                currentSession.getSessionId(), text, System.currentTimeMillis());
    }

    private static class SessionCell extends ListCell<SessionModel> {
        @Override
        protected void updateItem(SessionModel session, boolean empty) {
            super.updateItem(session, empty);
            if (empty || session == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            HBox root = new HBox(10);
            root.setPadding(new Insets(8));
            root.setAlignment(Pos.CENTER_LEFT);

            StackPane avatarPane = new StackPane();
            Circle avatarCircle = new Circle(22, Color.web("#07C160"));
            Label avatarLabel = new Label(session.getName().substring(0, 1).toUpperCase());
            avatarLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
            avatarPane.getChildren().add(avatarCircle);
            avatarPane.getChildren().add(avatarLabel);

            VBox infoBox = new VBox(2);
            HBox.setHgrow(infoBox, Priority.ALWAYS);

            Label nameLabel = new Label(session.getName());
            nameLabel.setStyle("-fx-text-fill: #181818; -fx-font-size: 14px; -fx-font-weight: bold;");

            Label lastMsgLabel = new Label(session.getLastMsg());
            lastMsgLabel.setStyle("-fx-text-fill: #999999; -fx-font-size: 12px;");
            lastMsgLabel.setWrapText(true);
            lastMsgLabel.setMaxHeight(18);

            infoBox.getChildren().addAll(nameLabel, lastMsgLabel);

            VBox rightBox = new VBox(2);
            rightBox.setAlignment(Pos.CENTER_RIGHT);

            String timeStr = formatTime(session.getLastMsgTime());
            Label timeLabel = new Label(timeStr);
            timeLabel.setStyle("-fx-text-fill: #999999; -fx-font-size: 11px;");

            rightBox.getChildren().add(timeLabel);

            if (session.getUnreadCount() > 0) {
                Label badge = new Label(String.valueOf(session.getUnreadCount()));
                badge.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                        "-fx-font-size: 10px; -fx-padding: 2 5; -fx-background-radius: 8;");
                rightBox.getChildren().add(badge);
            }

            root.getChildren().addAll(avatarPane, infoBox, rightBox);
            setGraphic(root);
        }
    }

    private static class MessageCell extends ListCell<MessageModel> {
        @Override
        protected void updateItem(MessageModel message, boolean empty) {
            super.updateItem(message, empty);
            if (empty || message == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            HBox root = new HBox();
            root.setPadding(new Insets(4, 12, 4, 12));

            Label bubble = new Label(message.getText());
            bubble.setWrapText(true);
            bubble.setMaxWidth(400);
            bubble.setPadding(new Insets(8, 12, 8, 12));

            if (message.isSentByMe()) {
                root.setAlignment(Pos.CENTER_RIGHT);
                bubble.setStyle("-fx-background-color: #95EC69; -fx-text-fill: #181818; " +
                        "-fx-background-radius: 4 12 12 12; -fx-font-size: 14px;");
            } else {
                root.setAlignment(Pos.CENTER_LEFT);
                bubble.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #181818; " +
                        "-fx-background-radius: 12 4 12 12; -fx-font-size: 14px;");
            }

            root.getChildren().add(bubble);
            setGraphic(root);
        }
    }

    private static String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.now();
        if (dt.toLocalDate().equals(now.toLocalDate())) {
            return dt.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return dt.format(DateTimeFormatter.ofPattern("MM/dd"));
    }

    @FXML
    private void onLogout() {
        JwtUtil.clearToken();
        try {
            com.im.client.network.TcpConnection.getInstance().disconnect();
            com.im.client.App.navigateToLogin();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
