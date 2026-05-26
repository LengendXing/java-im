package com.im.server.e2ee;

public class E2eeKeyServiceHolder {
    private static E2eeKeyService instance = new E2eeKeyService();
    public static E2eeKeyService getInstance() { return instance; }
}
