package com.im.server.registry;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.NacosNamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class NacosRegistryService {
    private static final Logger log = LoggerFactory.getLogger(NacosRegistryService.class);
    private static final String SERVICE_NAME = "im-server";

    private NamingService namingService;
    private boolean enabled;

    public void init(String serverAddr, boolean enabled) throws NacosException {
        this.enabled = enabled;
        if (!enabled) {
            log.info("Nacos registry disabled");
            return;
        }
        Properties props = new Properties();
        props.put("serverAddr", serverAddr);
        props.put("namespace", "public");
        namingService = new NacosNamingService(props);
        log.info("Nacos registry connected to {}", serverAddr);
    }

    public void register(String ip, int port, String groupName) {
        if (!enabled) return;
        try {
            Instance instance = new Instance();
            instance.setIp(ip);
            instance.setPort(port);
            instance.setServiceName(SERVICE_NAME);
            instance.setHealthy(true);
            instance.setWeight(1.0);
            namingService.registerInstance(SERVICE_NAME, groupName, instance);
            log.info("Registered im-server instance {}:{}", ip, port);
        } catch (NacosException e) {
            log.warn("Nacos register failed: {}", e.getMessage());
        }
    }

    public void deregister(String ip, int port, String groupName) {
        if (!enabled) return;
        try {
            namingService.deregisterInstance(SERVICE_NAME, groupName, ip, port);
            log.info("Deregistered im-server instance {}:{}", ip, port);
        } catch (NacosException e) {
            log.warn("Nacos deregister failed: {}", e.getMessage());
        }
    }

    public List<String> getGatewayInstances() {
        if (!enabled || namingService == null) return List.of();
        try {
            return namingService.selectInstances("im-gateway", "im", true)
                    .stream().map(i -> i.getIp() + ":" + i.getPort())
                    .collect(Collectors.toList());
        } catch (NacosException e) {
            log.warn("Nacos lookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean isEnabled() { return enabled; }

    public void close() {
        if (namingService != null) {
            try { namingService.shutDown(); } catch (Exception ignored) {}
        }
    }
}
