package com.im.client.controller;

import com.im.client.App;
import com.im.client.network.ResponseHandler;
import com.im.client.network.TcpConnection;
import com.im.client.service.AuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;
    @FXML private Label registerLink;
    @FXML private StackPane logoPane;

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
        loginButton.setDefaultButton(true);
        setupLogo();
    }

    private void setupLogo() {
        if (logoPane == null) return;
        Circle bg = new Circle(32, Color.web("#07C160"));
        Label icon = new Label("W");
        icon.setTextFill(Color.WHITE);
        icon.setFont(Font.font("System", FontWeight.BOLD, 28));
        icon.setAlignment(Pos.CENTER);
        logoPane.getChildren().addAll(bg, icon);
        logoPane.setPrefSize(64, 64);
    }

    @FXML
    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter username and password");
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setVisible(false);

        new Thread(() -> {
            AuthService.AuthResult result = AuthService.getInstance().login(username, password);
            Platform.runLater(() -> {
                loginButton.setDisable(false);
                if (result.success()) {
                    connectTcp();
                    try {
                        App.navigateToMain();
                    } catch (Exception e) {
                        log.error("Navigation error: {}", e.getMessage());
                    }
                } else {
                    showError(result.message());
                }
            });
        }).start();
    }

    private void connectTcp() {
        TcpConnection tcp = TcpConnection.getInstance();
        tcp.setResponseHandler(new ResponseHandler());
        tcp.connect();
    }

    @FXML
    private void onRegisterLink() {
        try {
            App.navigateToRegister();
        } catch (Exception e) {
            log.error("Navigation error: {}", e.getMessage());
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }
}
