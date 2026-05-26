package com.im.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        primaryStage.setTitle("IM Client");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        navigateToLogin();

        primaryStage.show();
    }

    public static void navigateToLogin() throws Exception {
        Parent root = FXMLLoader.load(App.class.getResource("/login.fxml"));
        Scene scene = new Scene(root, 360, 480);
        scene.getStylesheets().add(App.class.getResource("/style.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("IM Client - Login");
    }

    public static void navigateToRegister() throws Exception {
        Parent root = FXMLLoader.load(App.class.getResource("/register.fxml"));
        Scene scene = new Scene(root, 360, 520);
        scene.getStylesheets().add(App.class.getResource("/style.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("IM Client - Register");
    }

    public static void navigateToMain() throws Exception {
        Parent root = FXMLLoader.load(App.class.getResource("/main.fxml"));
        Scene scene = new Scene(root, 1000, 700);
        scene.getStylesheets().add(App.class.getResource("/style.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("IM Client");
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    @Override
    public void stop() {
        try {
            com.im.client.network.TcpConnection.getInstance().disconnect();
        } catch (Exception ignored) {}
    }
}
