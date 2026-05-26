package com.im.server;

import com.im.server.common.*;
import com.im.server.storage.DatabaseService;
import com.im.server.storage.RedisService;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        VertxOptions options = new VertxOptions()
                .setEventLoopPoolSize(Runtime.getRuntime().availableProcessors() * 2)
                .setWorkerPoolSize(20);

        Vertx vertx = Vertx.vertx(options);

        // Load config from application.json
        vertx.fileSystem().readFile("application.json")
                .onSuccess(buf -> {
                    JsonObject configJson = buf.toJsonObject();
                    ServerConfig serverConfig = ServerConfig.fromJson(configJson);
                    log.info("Config loaded: tcp={}, ws={}, http={}", serverConfig.getTcpPort(), serverConfig.getWsPort(), serverConfig.getHttpPort());

                    // Initialize shared services
                    RedisService redisService = new RedisService();
                    DatabaseService dbService = new DatabaseService();
                    SnowflakeId snowflakeId = new SnowflakeId(serverConfig.getWorkerId());

                    // Store in holders for verticle access
                    RedisServiceHolder.setInstance(redisService);
                    DatabaseServiceHolder.setInstance(dbService);
                    SnowflakeIdHolder.setInstance(snowflakeId);

                    // Initialize Redis and MySQL
                    redisService.init(vertx, serverConfig.getRedisHost(), serverConfig.getRedisPort(),
                                    serverConfig.getRedisPassword(), serverConfig.getRedisDatabase())
                            .compose(v -> dbService.init(vertx, serverConfig.getMysqlHost(), serverConfig.getMysqlPort(),
                                    serverConfig.getMysqlDatabase(), serverConfig.getMysqlUser(), serverConfig.getMysqlPassword()))
                            .compose(v -> {
                                log.info("Storage services initialized");
                                return dbService.initSchema();
                            })
                            .compose(v -> {
                                log.info("Database schema verified");
                                // Deploy verticles with config
                                JsonObject verticleConfig = serverConfig.toJson();
                                DeploymentOptions depOpts = new DeploymentOptions().setConfig(verticleConfig);

                                return deployVerticles(vertx, depOpts);
                            })
                            .onSuccess(v -> log.info("java-im server v0.2.0 started"))
                            .onFailure(err -> {
                                log.error("Server startup failed: {}", err.getMessage());
                                vertx.close();
                            });
                })
                .onFailure(err -> {
                    log.error("Failed to load config: {}", err.getMessage());
                    // Use defaults
                    ServerConfig serverConfig = new ServerConfig();
                    RedisService redisService = new RedisService();
                    DatabaseService dbService = new DatabaseService();
                    SnowflakeId snowflakeId = new SnowflakeId(1);

                    RedisServiceHolder.setInstance(redisService);
                    DatabaseServiceHolder.setInstance(dbService);
                    SnowflakeIdHolder.setInstance(snowflakeId);

                    JsonObject verticleConfig = serverConfig.toJson();
                    DeploymentOptions depOpts = new DeploymentOptions().setConfig(verticleConfig);

                    deployVerticles(vertx, depOpts)
                            .onSuccess(v -> log.info("java-im server v0.2.0 started (default config)"))
                            .onFailure(e -> {
                                log.error("Server startup failed: {}", e.getMessage());
                                vertx.close();
                            });
                });

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            vertx.close();
        }));
    }

    private static io.vertx.core.Future<Void> deployVerticles(Vertx vertx, DeploymentOptions depOpts) {
        return vertx.deployVerticle("com.im.server.gateway.GatewayVerticle", depOpts)
                .compose(id -> { log.info("GatewayVerticle deployed: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vertx.deployVerticle("com.im.server.gateway.HttpApiVerticle", depOpts))
                .compose(id -> { log.info("HttpApiVerticle deployed: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vertx.deployVerticle("com.im.server.logic.AuthVerticle", depOpts))
                .compose(id -> { log.info("AuthVerticle deployed: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vertx.deployVerticle("com.im.server.logic.C2CVerticle",
                        new DeploymentOptions().setConfig(depOpts.getConfig()).setInstances(2)))
                .compose(id -> { log.info("C2CVerticle deployed x2: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vertx.deployVerticle("com.im.server.logic.GroupVerticle", depOpts))
                .compose(id -> { log.info("GroupVerticle deployed: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vertx.deployVerticle("com.im.server.logic.SessionVerticle", depOpts))
                .compose(id -> { log.info("SessionVerticle deployed: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vertx.deployVerticle("com.im.server.logic.PushVerticle",
                        new DeploymentOptions().setConfig(depOpts.getConfig()).setInstances(2)))
                .compose(id -> { log.info("PushVerticle deployed x2: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .mapEmpty();
    }
}
