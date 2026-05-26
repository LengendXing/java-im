package com.im.client.controller;

import com.im.client.model.MessageModel;
import com.im.client.model.SessionModel;
import com.im.client.service.*;
import com.im.client.util.JwtUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML private ListView<SessionModel> sessionListView;
    @FXML private ListView<FriendService.FriendRequest> friendRequestListView;
    @FXML private Label chatTargetLabel;
    @FXML private ListView<MessageModel> messageListView;
    @FXML private TextArea messageInput;
    @FXML private Button sendButton;
    @FXML private Button fileUploadButton;
    @FXML private Button groupInviteButton;
    @FXML private Button groupKickButton;
    @FXML private Button groupDissolveButton;

    private SessionModel currentSession;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSessionList();
        setupFriendRequestList();
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

    private void setupFriendRequestList() {
        friendRequestListView.setItems(FriendService.getInstance().getPendingRequests());
        friendRequestListView.setCellFactory(list -> new FriendRequestCell());
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

    // ===== Session Selection & Message Read =====

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
        fileUploadButton.setDisable(false);

        // Mark messages as read
        if (session.getLastReadSeq() >= 0) {
            MessageService.getInstance().markRead(session.getSessionId(), session.getLastReadSeq());
        }

        // Show group management buttons for group sessions
        boolean isGroup = session.getType() == 2;
        groupInviteButton.setVisible(isGroup);
        groupKickButton.setVisible(isGroup);
        groupDissolveButton.setVisible(isGroup);
    }

    // ===== Send Text Message =====

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
        msg.setMsgType(MessageModel.MSG_TYPE_TEXT);
        msg.setSentByMe(true);
        msg.setServerTime(System.currentTimeMillis());

        currentSession.getMessages().add(msg);
        messageInput.clear();

        SessionService.getInstance().updateSessionLastMsg(
                currentSession.getSessionId(), text, System.currentTimeMillis());
    }

    // ===== File Upload =====

    @FXML
    private void onFileUpload() {
        if (currentSession == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        File file = fileChooser.showOpenDialog(getStage());
        if (file == null) return;

        // Determine msgType based on file extension
        String fileName = file.getName().toLowerCase();
        int msgType;
        String displayText;
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                fileName.endsWith(".bmp") || fileName.endsWith(".webp")) {
            msgType = MessageModel.MSG_TYPE_IMAGE;
            displayText = "[Image]";
        } else {
            msgType = MessageModel.MSG_TYPE_FILE;
            displayText = "[File] " + file.getName();
        }

        int finalMsgType = msgType;
        String finalDisplayText = displayText;

        new Thread(() -> {
            FileUploadService.UploadResult result = FileUploadService.getInstance().upload(file);
            if (result == null) {
                Platform.runLater(() -> showAlert("Upload Failed", "Could not upload file."));
                return;
            }

            String clientMsgId;
            if (currentSession.getType() == 1) {
                clientMsgId = ChatService.getInstance().sendC2CMessage(
                        currentSession.getTargetId(), finalMsgType, finalDisplayText, result.url());
            } else {
                clientMsgId = ChatService.getInstance().sendGroupMessage(
                        currentSession.getTargetId(), finalMsgType, finalDisplayText, result.url());
            }

            MessageModel msg = new MessageModel();
            msg.setClientMsgId(clientMsgId);
            msg.setSessionId(currentSession.getSessionId());
            msg.setText(finalDisplayText);
            msg.setMsgType(finalMsgType);
            msg.setUrl(result.url());
            msg.setFileName(result.fileName());
            msg.setFileSize(result.fileSize());
            msg.setSentByMe(true);
            msg.setServerTime(System.currentTimeMillis());

            Platform.runLater(() -> {
                currentSession.getMessages().add(msg);
                SessionService.getInstance().updateSessionLastMsg(
                        currentSession.getSessionId(), finalDisplayText, System.currentTimeMillis());
            });
        }).start();
    }

    // ===== Friend Requests =====

    @FXML
    private void onFriendRequestsTabChanged() {
        FriendService.getInstance().loadPendingRequests();
    }

    // ===== Group Management =====

    @FXML
    private void onCreateGroup() {
        TextInputDialog nameDialog = new TextInputDialog("New Group");
        nameDialog.setTitle("Create Group");
        nameDialog.setHeaderText("Enter group name:");
        nameDialog.setContentText("Name:");
        Optional<String> nameResult = nameDialog.showAndWait();
        if (nameResult.isEmpty() || nameResult.get().trim().isEmpty()) return;

        String groupName = nameResult.get().trim();

        // Get member IDs
        TextInputDialog memberDialog = new TextInputDialog();
        memberDialog.setTitle("Add Members");
        memberDialog.setHeaderText("Enter member user IDs (comma-separated):");
        memberDialog.setContentText("User IDs:");
        Optional<String> memberResult = memberDialog.showAndWait();
        if (memberResult.isEmpty()) return;

        String[] idStrs = memberResult.get().split(",");
        long[] memberIds = new long[idStrs.length];
        try {
            for (int i = 0; i < idStrs.length; i++) {
                memberIds[i] = Long.parseLong(idStrs[i].trim());
            }
        } catch (NumberFormatException e) {
            showAlert("Invalid Input", "Please enter valid numeric user IDs.");
            return;
        }

        new Thread(() -> {
            long groupId = GroupService.getInstance().createGroup(groupName, memberIds);
            if (groupId > 0) {
                Platform.runLater(() -> {
                    SessionService.getInstance().requestSessionList();
                    showAlert("Group Created", "Group '" + groupName + "' created with ID: " + groupId);
                });
            } else {
                Platform.runLater(() -> showAlert("Failed", "Could not create group."));
            }
        }).start();
    }

    @FXML
    private void onGroupInvite() {
        if (currentSession == null || currentSession.getType() != 2) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Invite to Group");
        dialog.setHeaderText("Enter user IDs to invite (comma-separated):");
        dialog.setContentText("User IDs:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        String[] idStrs = result.get().split(",");
        long[] userIds = new long[idStrs.length];
        try {
            for (int i = 0; i < idStrs.length; i++) {
                userIds[i] = Long.parseLong(idStrs[i].trim());
            }
        } catch (NumberFormatException e) {
            showAlert("Invalid Input", "Please enter valid numeric user IDs.");
            return;
        }

        new Thread(() -> {
            boolean ok = GroupService.getInstance().invite(currentSession.getTargetId(), userIds);
            Platform.runLater(() -> {
                if (ok) {
                    showAlert("Invited", "Users invited to group.");
                } else {
                    showAlert("Failed", "Could not invite users.");
                }
            });
        }).start();
    }

    @FXML
    private void onGroupKick() {
        if (currentSession == null || currentSession.getType() != 2) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Kick Member");
        dialog.setHeaderText("Enter user ID to kick:");
        dialog.setContentText("User ID:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        long userId;
        try {
            userId = Long.parseLong(result.get().trim());
        } catch (NumberFormatException e) {
            showAlert("Invalid Input", "Please enter a valid numeric user ID.");
            return;
        }

        new Thread(() -> {
            boolean ok = GroupService.getInstance().kick(currentSession.getTargetId(), userId);
            Platform.runLater(() -> {
                if (ok) {
                    showAlert("Kicked", "User kicked from group.");
                } else {
                    showAlert("Failed", "Could not kick user. Are you the group owner?");
                }
            });
        }).start();
    }

    @FXML
    private void onGroupDissolve() {
        if (currentSession == null || currentSession.getType() != 2) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Dissolve Group");
        confirm.setHeaderText("Are you sure you want to dissolve this group?");
        confirm.setContentText("This action cannot be undone.");
        Optional<ButtonType> btn = confirm.showAndWait();
        if (btn.isEmpty() || btn.get() != ButtonType.OK) return;

        new Thread(() -> {
            boolean ok = GroupService.getInstance().dissolve(currentSession.getTargetId());
            Platform.runLater(() -> {
                if (ok) {
                    showAlert("Dissolved", "Group has been dissolved.");
                    SessionService.getInstance().requestSessionList();
                } else {
                    showAlert("Failed", "Could not dissolve group. Are you the group owner?");
                }
            });
        }).start();
    }

    // ===== Logout =====

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

    // ===== Helper =====

    private Stage getStage() {
        return (Stage) messageInput.getScene().getWindow();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ===== Session List Cell =====

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

    // ===== Friend Request Cell =====

    private static class FriendRequestCell extends ListCell<FriendService.FriendRequest> {
        @Override
        protected void updateItem(FriendService.FriendRequest request, boolean empty) {
            super.updateItem(request, empty);
            if (empty || request == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            HBox root = new HBox(10);
            root.setPadding(new Insets(8));
            root.setAlignment(Pos.CENTER_LEFT);

            StackPane avatarPane = new StackPane();
            Circle avatarCircle = new Circle(18, Color.web("#07C160"));
            Label avatarLabel = new Label(request.toString().substring(0, 1).toUpperCase());
            avatarLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
            avatarPane.getChildren().add(avatarCircle);
            avatarPane.getChildren().add(avatarLabel);

            Label nameLabel = new Label(request.toString());
            nameLabel.setStyle("-fx-text-fill: #181818; -fx-font-size: 13px;");
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            Button acceptBtn = new Button("Accept");
            acceptBtn.setStyle("-fx-background-color: #07C160; -fx-text-fill: white; " +
                    "-fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 4;");
            acceptBtn.setOnAction(e -> {
                new Thread(() -> FriendService.getInstance().accept(request.getFromUserId())).start();
            });

            Button rejectBtn = new Button("Reject");
            rejectBtn.setStyle("-fx-background-color: #FA5151; -fx-text-fill: white; " +
                    "-fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 4;");
            rejectBtn.setOnAction(e -> {
                new Thread(() -> FriendService.getInstance().reject(request.getFromUserId())).start();
            });

            root.getChildren().addAll(avatarPane, nameLabel, acceptBtn, rejectBtn);
            setGraphic(root);
        }
    }

    // ===== Message Cell with Image, File, Recall =====

    private class MessageCell extends ListCell<MessageModel> {
        private ContextMenu contextMenu;

        @Override
        protected void updateItem(MessageModel message, boolean empty) {
            super.updateItem(message, empty);
            if (empty || message == null) {
                setGraphic(null);
                setText(null);
                setContextMenu(null);
                return;
            }

            // Recalled message
            if (message.isRecalled()) {
                HBox root = new HBox();
                root.setAlignment(Pos.CENTER);
                Label recallLabel = new Label("Message recalled");
                recallLabel.setStyle("-fx-text-fill: #999999; -fx-font-size: 12px; -fx-font-style: italic;");
                root.getChildren().add(recallLabel);
                setGraphic(root);
                setContextMenu(null);
                return;
            }

            HBox root = new HBox();
            root.setPadding(new Insets(4, 12, 4, 12));

            if (message.isSentByMe()) {
                root.setAlignment(Pos.CENTER_RIGHT);
            } else {
                root.setAlignment(Pos.CENTER_LEFT);
            }

            int msgType = message.getMsgType();

            if (msgType == MessageModel.MSG_TYPE_IMAGE && message.getUrl() != null
                    && !message.getUrl().isEmpty()) {
                // Image message: show inline image
                ImageView imageView = new ImageView();
                imageView.setFitWidth(200);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);

                try {
                    Image img = new Image(message.getUrl(), true);
                    imageView.setImage(img);
                } catch (Exception e) {
                    // Fallback to text if image load fails
                    Label fallback = createBubble(message.getText(), message.isSentByMe());
                    root.getChildren().add(fallback);
                    setGraphic(root);
                    setupRecallContextMenu(message);
                    return;
                }

                // Wrap image in a styled pane
                StackPane imgPane = new StackPane(imageView);
                imgPane.setPadding(new Insets(4));
                if (message.isSentByMe()) {
                    imgPane.setStyle("-fx-background-color: #95EC69; -fx-background-radius: 4 12 12 12;");
                } else {
                    imgPane.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 12 4 12 12;");
                }
                root.getChildren().add(imgPane);

            } else if (msgType == MessageModel.MSG_TYPE_FILE) {
                // File message: show file name + size
                VBox fileBox = new VBox(4);
                fileBox.setPadding(new Insets(8, 12, 8, 12));
                fileBox.setMaxWidth(300);

                Label fileIcon = new Label("📄 " + message.getFileName());
                fileIcon.setStyle("-fx-text-fill: #181818; -fx-font-size: 14px; -fx-font-weight: bold;");

                String sizeText = formatFileSize(message.getFileSize());
                Label sizeLabel = new Label(sizeText);
                sizeLabel.setStyle("-fx-text-fill: #999999; -fx-font-size: 11px;");

                fileBox.getChildren().addAll(fileIcon, sizeLabel);

                if (message.isSentByMe()) {
                    fileBox.setStyle("-fx-background-color: #95EC69; -fx-background-radius: 4 12 12 12;");
                } else {
                    fileBox.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 12 4 12 12;");
                }
                root.getChildren().add(fileBox);

            } else {
                // Text message (default)
                Label bubble = createBubble(message.getText(), message.isSentByMe());
                root.getChildren().add(bubble);
            }

            setGraphic(root);
            setupRecallContextMenu(message);
        }

        private Label createBubble(String text, boolean sentByMe) {
            Label bubble = new Label(text);
            bubble.setWrapText(true);
            bubble.setMaxWidth(400);
            bubble.setPadding(new Insets(8, 12, 8, 12));

            if (sentByMe) {
                bubble.setStyle("-fx-background-color: #95EC69; -fx-text-fill: #181818; " +
                        "-fx-background-radius: 4 12 12 12; -fx-font-size: 14px;");
            } else {
                bubble.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #181818; " +
                        "-fx-background-radius: 12 4 12 12; -fx-font-size: 14px;");
            }
            return bubble;
        }

        private void setupRecallContextMenu(MessageModel message) {
            if (!message.isSentByMe()) {
                setContextMenu(null);
                return;
            }

            // Only allow recall within 2 minutes
            long elapsed = System.currentTimeMillis() - message.getServerTime();
            if (elapsed > 2 * 60 * 1000) {
                setContextMenu(null);
                return;
            }

            contextMenu = new ContextMenu();
            MenuItem recallItem = new MenuItem("Recall");
            recallItem.setOnAction(e -> onRecallMessage(message));
            contextMenu.getItems().add(recallItem);
            setContextMenu(contextMenu);
        }

        private void onRecallMessage(MessageModel message) {
            if (message.getMsgId() <= 0) {
                showAlert("Cannot Recall", "Message not yet confirmed by server.");
                return;
            }

            new Thread(() -> {
                boolean ok = MessageService.getInstance().recall(message.getMsgId(), message.getSessionId());
                if (ok) {
                    Platform.runLater(() -> message.setRecalled(true));
                } else {
                    Platform.runLater(() -> showAlert("Recall Failed", "Could not recall message. It may be older than 2 minutes."));
                }
            }).start();
        }
    }

    // ===== Utility =====

    private static String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.now();
        if (dt.toLocalDate().equals(now.toLocalDate())) {
            return dt.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return dt.format(DateTimeFormatter.ofPattern("MM/dd"));
    }

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIdx = 0;
        double size = bytes;
        while (size >= 1024 && unitIdx < units.length - 1) {
            size /= 1024;
            unitIdx++;
        }
        return String.format("%.1f %s", size, units[unitIdx]);
    }
}
