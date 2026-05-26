package com.im.server.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ApnsService {
    private static final Logger log = LoggerFactory.getLogger(ApnsService.class);

    private boolean enabled;
    private String bundleId;
    private String p8Path;
    private String teamId;
    private String keyId;

    public ApnsService(boolean enabled, String bundleId, String p8Path, String teamId, String keyId) {
        this.enabled = enabled;
        this.bundleId = bundleId;
        this.p8Path = p8Path;
        this.teamId = teamId;
        this.keyId = keyId;
    }

    public boolean isEnabled() { return enabled; }

    public void push(String deviceToken, String title, String body, int badge) {
        if (!enabled) {
            log.debug("APNs disabled, skip push to {}", deviceToken);
            return;
        }
        // TODO: Integrate pushy library for actual APNs HTTP/2 push
        // Requires Apple Developer Account + .p8 key file
        log.info("APNs push to token={}, title={}, body={}, badge={}", deviceToken, title, body, badge);
    }
}
