package com.im.server.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;

public class FcmService {
    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    private boolean enabled;
    private String credentialsPath;
    private FirebaseMessaging messaging;

    public FcmService(boolean enabled, String credentialsPath) {
        this.enabled = enabled;
        this.credentialsPath = credentialsPath;
        if (enabled) initClient();
    }

    private void initClient() {
        try {
            InputStream credStream;
            if (credentialsPath != null && !credentialsPath.isEmpty()) {
                credStream = new FileInputStream(credentialsPath);
            } else {
                log.warn("FCM credentials path not set, FCM push disabled");
                enabled = false;
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credStream))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            messaging = FirebaseMessaging.getInstance();
            log.info("FCM client initialized from {}", credentialsPath);
        } catch (Exception e) {
            log.error("FCM client init failed: {}", e.getMessage());
            enabled = false;
        }
    }

    public boolean isEnabled() { return enabled; }

    public void push(String deviceToken, String title, String body) {
        if (!enabled || messaging == null) {
            log.debug("FCM disabled, skip push to {}", deviceToken);
            return;
        }
        try {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                    .build();

            messaging.sendAsync(message).addListener(() -> {
                log.debug("FCM push sent to token={}", deviceToken);
            }, Runnable::run);
        } catch (Exception e) {
            log.warn("FCM push exception: {}", e.getMessage());
        }
    }
}
