package com.im.server.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FcmService {
    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    private boolean enabled;
    private String credentialsPath;

    public FcmService(boolean enabled, String credentialsPath) {
        this.enabled = enabled;
        this.credentialsPath = credentialsPath;
    }

    public boolean isEnabled() { return enabled; }

    public void push(String deviceToken, String title, String body) {
        if (!enabled) {
            log.debug("FCM disabled, skip push to {}", deviceToken);
            return;
        }
        // TODO: Integrate firebase-admin SDK for actual FCM push
        // Requires Google service account JSON key file
        log.info("FCM push to token={}, title={}, body={}", deviceToken, title, body);
    }
}
