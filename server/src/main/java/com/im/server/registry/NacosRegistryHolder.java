package com.im.server.registry;

public class NacosRegistryHolder {
    private static NacosRegistryService instance;
    public static void setInstance(NacosRegistryService service) { instance = service; }
    public static NacosRegistryService getInstance() { return instance; }
}
