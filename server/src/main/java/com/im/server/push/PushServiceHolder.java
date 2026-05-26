package com.im.server.push;

import com.im.server.common.RedisServiceHolder;
import com.im.server.storage.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushServiceHolder {
    private static ApnsService apnsService;
    private static FcmService fcmService;
    private static PushRateLimiter rateLimiter;

    public static void init(ApnsService apns, FcmService fcm) {
        apnsService = apns;
        fcmService = fcm;
        rateLimiter = new PushRateLimiter();
    }

    public static ApnsService getApnsService() { return apnsService; }
    public static FcmService getFcmService() { return fcmService; }
    public static PushRateLimiter getRateLimiter() { return rateLimiter; }
}
