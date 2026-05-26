package com.im.server.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NacosConfigHolder {
    private static final Logger log = LoggerFactory.getLogger(NacosConfigHolder.class);
    private static NacosConfigService instance;

    public static void setInstance(NacosConfigService svc) { instance = svc; }
    public static NacosConfigService getInstance() { return instance; }
}
