package com.im.server.registry;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class NacosConfigService {
    private static final Logger log = LoggerFactory.getLogger(NacosConfigService.class);
    private static final String DATA_ID = "im-server.yml";
    private static final String GROUP = "im";

    private ConfigService configService;
    private boolean enabled;
    private JsonObject currentConfig;
    private Runnable onConfigChange;

    public void init(String serverAddr, boolean enabled, Runnable onConfigChange) throws NacosException {
        this.enabled = enabled;
        this.onConfigChange = onConfigChange;
        if (!enabled) {
            log.info("Nacos config center disabled");
            return;
        }
        Properties props = new Properties();
        props.put("serverAddr", serverAddr);
        props.put("namespace", "public");
        configService = NacosFactory.createConfigService(props);

        String content = configService.getConfig(DATA_ID, GROUP, 5000);
        if (content != null && !content.isEmpty()) {
            currentConfig = parseYamlContent(content);
            log.info("Nacos config loaded: dataId={}", DATA_ID);
        }

        configService.addListener(DATA_ID, GROUP, new Listener() {
            final Executor executor = Executors.newSingleThreadExecutor();
            @Override public Executor getExecutor() { return executor; }
            @Override public void receiveConfigInfo(String configInfo) {
                log.info("Nacos config changed: dataId={}", DATA_ID);
                currentConfig = parseYamlContent(configInfo);
                if (onConfigChange != null) onConfigChange.run();
            }
        });
        log.info("Nacos config center connected to {}", serverAddr);
    }

    public JsonObject getConfig() { return currentConfig; }
    public boolean isEnabled() { return enabled; }

    public void close() {
        if (configService != null) {
            try { configService.shutDown(); } catch (Exception ignored) {}
        }
    }

    private JsonObject parseYamlContent(String yaml) {
        if (yaml == null || yaml.isEmpty()) return new JsonObject();
        try {
            // Simple key:value parsing for flat YAML config
            JsonObject json = new JsonObject();
            for (String line : yaml.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int idx = line.indexOf(':');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String val = line.substring(idx + 1).trim();
                    if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
                    json.put(key, val);
                }
            }
            return json;
        } catch (Exception e) {
            log.warn("Failed to parse nacos config yaml: {}", e.getMessage());
            return new JsonObject();
        }
    }
}
