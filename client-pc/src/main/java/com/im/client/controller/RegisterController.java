package com.im.client.controller;

import com.im.client.App;
import com.im.client.service.AuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterController {

    private static final Logger log = LoggerFactory.getLogger(RegisterController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button registerButton;
    @FXML private Label errorLabel;
    @FXML private Label backLink;
    @FXML private StackPane logoPane;

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
        registerButton.setDefaultButton(true);
        setupLogo();
    }

    private void setupLogo() {
        if (logoPane == null) return;
        Circle bg = new Circle(28, Color.web("#07C160"));
        Label icon = new Label("W");
        icon.setTextFill(Color.WHITE);
        icon.setFont(Font.font("System", FontWeight.BOLD, 24));
        icon.setAlignment(Pos.CENTER);
        logoPane.getChildren().addAll(bg, icon);
        logoPane.setPrefSize(56, 56);
    }

    @FXML
    private void onRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String confirmPassword = confirmPasswordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters");
            return;
        }

        registerButton.setDisable(true);
        errorLabel.setVisible(false);

        new Thread(() -> {
            AuthService.AuthResult result = AuthService.getInstance().register(username, password);
            Platform.runLater(() -> {
                registerButton.setDisable(false);
                if (result.success()) {
                    try {
                        App.navigateToLogin();
                    } catch (Exception e) {
                        log.error("Navigation error: {}", e.getMessage());
                    }
                } else {
                    showError(result.message());
                }
            });
        }).start();
    }

    @FXML
    private void onBackLink() {
        try {
            App.navigateToLogin();
        } catch (Exception e) {
            log.error("Navigation error: {}", e.getMessage());
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }
}
