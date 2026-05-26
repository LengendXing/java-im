package com.im.server.mq;

import org.noear.folkmq.FolkMQ;
import org.noear.folkmq.client.MqClient;
import org.noear.folkmq.client.MqMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FolkmqService {
    private static final Logger log = LoggerFactory.getLogger(FolkmqService.class);

    private MqClient client;

    public void init(String host, int port, String appName) throws java.io.IOException {
        client = FolkMQ.createClient("folkmq://" + host + ":" + port)
                .nameAs(appName)
                .connect();
        log.info("Folkmq client connected to {}:{} as {}", host, port, appName);
    }

    public void publish(String topic, byte[] payload) {
        try {
            client.publish(topic, new MqMessage(payload));
        } catch (java.io.IOException e) {
            log.error("Folkmq publish error on topic {}: {}", topic, e.getMessage());
        }
    }

    public void publishOrdered(String topic, byte[] payload, String shardingKey) {
        try {
            client.publish(topic, new MqMessage(payload).sequence(true, shardingKey));
        } catch (java.io.IOException e) {
            log.error("Folkmq publish error on topic {} sharding {}: {}", topic, shardingKey, e.getMessage());
        }
    }

    public void subscribe(String topic, java.util.function.Consumer<byte[]> handler) {
        try {
            client.subscribe(topic, message -> {
                try {
                    handler.accept(message.getBody());
                } catch (Exception e) {
                    log.error("Folkmq subscriber error on topic {}: {}", topic, e.getMessage());
                }
            });
        } catch (java.io.IOException e) {
            log.error("Folkmq subscribe error on topic {}: {}", topic, e.getMessage());
        }
    }

    public void close() {
        if (client != null) {
            try {
                client.disconnect();
                log.info("Folkmq client disconnected");
            } catch (java.io.IOException e) {
                log.error("Folkmq disconnect error: {}", e.getMessage());
            }
        }
    }
}
