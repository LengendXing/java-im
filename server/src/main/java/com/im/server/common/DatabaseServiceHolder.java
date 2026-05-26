package com.im.server.common;

import com.im.server.storage.DatabaseService;

public class DatabaseServiceHolder {
    private static DatabaseService instance;

    public static void setInstance(DatabaseService db) { instance = db; }
    public static DatabaseService getInstance() { return instance; }
}
