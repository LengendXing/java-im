package com.im.server.common;

public class SnowflakeIdHolder {
    private static SnowflakeId instance;

    public static void setInstance(SnowflakeId id) { instance = id; }
    public static SnowflakeId getInstance() { return instance; }
    public static long nextId() { return instance.nextId(); }
}
