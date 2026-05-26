package com.im.server.logic;

import com.im.server.common.RedisServiceHolder;
import com.im.server.common.DatabaseServiceHolder;

import com.im.server.storage.DatabaseService;
import com.im.server.storage.RedisService;

public class ServiceAccess {
    public static RedisService redis() { return RedisServiceHolder.getInstance(); }
    public static DatabaseService db() { return DatabaseServiceHolder.getInstance(); }
}
