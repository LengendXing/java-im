package com.im.server.common;

import com.im.server.storage.RedisService;

public class RedisServiceHolder {
    private static RedisService instance;

    public static void setInstance(RedisService redis) { instance = redis; }
    public static RedisService getInstance() { return instance; }
}
