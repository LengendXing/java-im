package com.im.client.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public final class Config {

    private static final String CONFIG_FILE = "client.properties";
    private static final Properties props = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (IOException ignored) {
        }
    }

    public static final String SERVER_HOST = getProperty("server.host", "127.0.0.1");
    public static final int SERVER_TCP_PORT = Integer.parseInt(getProperty("server.tcp.port", "8800"));
    public static final int SERVER_HTTP_PORT = Integer.parseInt(getProperty("server.http.port", "8080"));
    public static final int HEARTBEAT_INTERVAL_SEC = 30;

    private Config() {
    }

    private static String getProperty(String key, String defaultValue) {
        return System.getProperty(key, props.getProperty(key, defaultValue));
    }

    public static String getHttpBaseUrl() {
        return "http://" + SERVER_HOST + ":" + SERVER_HTTP_PORT;
    }

    public static void save(String key, String value) {
        props.setProperty(key, value);
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "IM Client Config");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
