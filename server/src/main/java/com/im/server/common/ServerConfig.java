package com.im.server.common;

import io.vertx.core.json.JsonObject;

public class ServerConfig {
    private int tcpPort;
    private int wsPort;
    private int httpPort;
    private JsonObject redis;
    private JsonObject mysql;
    private String jwtSecret;
    private int jwtTtlDays;
    private long workerId;
    private long heartbeatInterval;
    private long heartbeatTimeout;

    public static ServerConfig fromJson(JsonObject json) {
        ServerConfig cfg = new ServerConfig();
        cfg.tcpPort = Integer.parseInt(System.getProperty("IM_TCP_PORT",
                String.valueOf(json.getInteger("tcpPort", 8800))));
        cfg.wsPort = Integer.parseInt(System.getProperty("IM_WS_PORT",
                String.valueOf(json.getInteger("wsPort", 8801))));
        cfg.httpPort = Integer.parseInt(System.getProperty("IM_HTTP_PORT",
                String.valueOf(json.getInteger("httpPort", 8080))));
        cfg.redis = json.getJsonObject("redis", new JsonObject()
                .put("host", System.getProperty("IM_REDIS_HOST", "127.0.0.1"))
                .put("port", Integer.parseInt(System.getProperty("IM_REDIS_PORT", "6379")))
                .put("password", System.getProperty("IM_REDIS_PASSWORD", ""))
                .put("database", Integer.parseInt(System.getProperty("IM_REDIS_DATABASE", "0"))));
        cfg.mysql = json.getJsonObject("mysql", new JsonObject()
                .put("host", System.getProperty("IM_MYSQL_HOST", "127.0.0.1"))
                .put("port", Integer.parseInt(System.getProperty("IM_MYSQL_PORT", "3306")))
                .put("database", System.getProperty("IM_MYSQL_DB", "im_db"))
                .put("user", System.getProperty("IM_MYSQL_USER", "root"))
                .put("password", System.getProperty("IM_MYSQL_PASSWORD", "root")));
        cfg.jwtSecret = System.getProperty("IM_JWT_SECRET",
                json.getString("jwtSecret", "change-me-in-production"));
        cfg.jwtTtlDays = json.getInteger("jwtTtlDays", 7);
        cfg.workerId = json.getLong("workerId", 1L);
        cfg.heartbeatInterval = json.getLong("heartbeatInterval", 30000L);
        cfg.heartbeatTimeout = json.getLong("heartbeatTimeout", 90000L);
        return cfg;
    }

    public JsonObject toJson() {
        return new JsonObject()
                .put("tcpPort", tcpPort).put("wsPort", wsPort).put("httpPort", httpPort)
                .put("redis", redis).put("mysql", mysql)
                .put("jwtSecret", jwtSecret).put("jwtTtlDays", jwtTtlDays)
                .put("workerId", workerId)
                .put("heartbeatInterval", heartbeatInterval).put("heartbeatTimeout", heartbeatTimeout);
    }

    public int getTcpPort() { return tcpPort; }
    public int getWsPort() { return wsPort; }
    public int getHttpPort() { return httpPort; }
    public String getRedisHost() { return redis.getString("host"); }
    public int getRedisPort() { return redis.getInteger("port"); }
    public String getRedisPassword() { return redis.getString("password", ""); }
    public int getRedisDatabase() { return redis.getInteger("database", 0); }
    public String getMysqlHost() { return mysql.getString("host"); }
    public int getMysqlPort() { return mysql.getInteger("port"); }
    public String getMysqlDatabase() { return mysql.getString("database"); }
    public String getMysqlUser() { return mysql.getString("user"); }
    public String getMysqlPassword() { return mysql.getString("password"); }
    public String getJwtSecret() { return jwtSecret; }
    public int getJwtTtlDays() { return jwtTtlDays; }
    public long getWorkerId() { return workerId; }
    public long getHeartbeatInterval() { return heartbeatInterval; }
    public long getHeartbeatTimeout() { return heartbeatTimeout; }
}
