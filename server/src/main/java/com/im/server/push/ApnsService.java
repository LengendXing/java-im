package com.im.server.push;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class ApnsService {
    private static final Logger log = LoggerFactory.getLogger(ApnsService.class);

    private boolean enabled;
    private String bundleId;
    private String p8Path;
    private String teamId;
    private String keyId;
    private ApnsClient client;

    public ApnsService(boolean enabled, String bundleId, String p8Path, String teamId, String keyId) {
        this.enabled = enabled;
        this.bundleId = bundleId;
        this.p8Path = p8Path;
        this.teamId = teamId;
        this.keyId = keyId;
        if (enabled) initClient();
    }

    private void initClient() {
        try {
            File p8File = new File(p8Path);
            if (!p8File.exists()) {
                log.warn("APNs .p8 file not found: {}, APNs push disabled", p8Path);
                enabled = false;
                return;
            }
            client = new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setClientCredentials(p8File, teamId)
                    .build();
            log.info("APNs client initialized for bundleId={}", bundleId);
        } catch (Exception e) {
            log.error("APNs client init failed: {}", e.getMessage());
            enabled = false;
        }
    }

    public boolean isEnabled() { return enabled; }

    public void push(String deviceToken, String title, String body, int badge) {
        if (!enabled || client == null) {
            log.debug("APNs disabled, skip push to {}", deviceToken);
            return;
        }
        try {
            String payload = new SimpleApnsPayloadBuilder()
                    .setAlertTitle(title)
                    .setAlertBody(body)
                    .setBadgeNumber(badge)
                    .setSound("default")
                    .build();

            String token = TokenUtil.sanitizeTokenString(deviceToken);
            SimpleApnsPushNotification notification = new SimpleApnsPushNotification(token, bundleId, payload);
            CompletableFuture<PushNotificationResponse<SimpleApnsPushNotification>> future =
                    client.sendNotification(notification).whenComplete((response, cause) -> {
                        if (cause != null) {
                            log.warn("APNs push failed to token={}: {}", deviceToken, cause.getMessage());
                        } else if (!response.isAccepted()) {
                            log.warn("APNs rejected token={}: {}", deviceToken,
                                    response.getRejectionReason().orElse("unknown"));
                        } else {
                            log.debug("APNs push accepted for token={}", deviceToken);
                        }
                    });
        } catch (Exception e) {
            log.warn("APNs push exception: {}", e.getMessage());
        }
    }

    public void close() {
        if (client != null) {
            try { client.close().join(); } catch (Exception ignored) {}
        }
    }
}
