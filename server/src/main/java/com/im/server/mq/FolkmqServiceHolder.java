package com.im.server.mq;

public class FolkmqServiceHolder {
    private static FolkmqService instance;

    public static void setInstance(FolkmqService service) {
        instance = service;
    }

    public static FolkmqService getInstance() {
        return instance;
    }
}
