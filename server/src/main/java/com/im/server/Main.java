package com.im.server;

import com.im.server.common.*;
import com.im.server.mq.FolkmqService;
import com.im.server.mq.FolkmqServiceHolder;
import com.im.server.push.ApnsService;
import com.im.server.push.FcmService;
import com.im.server.push.PushServiceHolder;
import com.im.server.storage.DatabaseService;
import com.im.server.storage.RedisService;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private static Vertx vertx;

    public static void main(String[] args) {
        VertxOptions options = new VertxOptions()
                .setEventLoopPoolSize(Runtime.getRuntime().availableProcessors() * 2)
                .setWorkerPoolSize(20);

        vertx.fileSystem().readFile("application.json")
                .onSuccess(buf -> bootstrap(buf.toJsonObject(), options))
                .onFailure(err -> {
                    log.error("Failed to load config: {}", err.getMessage());
                    bootstrap(new JsonObject(), options);
                });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> gracefulShutdown()));
    }

    private static void bootstrap(JsonObject configJson, VertxOptions options) {
        ServerConfig serverConfig = ServerConfig.fromJson(configJson);

        if (serverConfig.isClusterEnabled()) {
            HazelcastClusterManager clusterManager = new HazelcastClusterManager();
            options.setClusterManager(clusterManager);
            log.info("Cluster mode enabled, using Hazelcast");

            Vertx.clusteredVertx(options)
                    .onSuccess(vx -> {
                        vertx = vx;
                        log.info("Clustered Vertx started");
                        initServices(vx, serverConfig);
                    })
                    .onFailure(err -> {
                        log.error("Clustered Vertx start failed, falling back to standalone: {}", err.getMessage());
                        vertx = Vertx.vertx(options);
                        initServices(vertx, serverConfig);
                    });
        } else {
            vertx = Vertx.vertx(options);
            log.info("Standalone mode");
            initServices(vertx, serverConfig);
        }
    }

    private static void initServices(Vertx vx, ServerConfig serverConfig) {
        RedisService redisService = new RedisService();
        DatabaseService dbService = new DatabaseService();
        SnowflakeId snowflakeId = new SnowflakeId(serverConfig.getWorkerId());
        FolkmqService folkmqService = new FolkmqService();

        RedisServiceHolder.setInstance(redisService);
        DatabaseServiceHolder.setInstance(dbService);
        SnowflakeIdHolder.setInstance(snowflakeId);
        FolkmqServiceHolder.setInstance(folkmqService);

        redisService.init(vx, serverConfig.getRedisHost(), serverConfig.getRedisPort(),
                        serverConfig.getRedisPassword(), serverConfig.getRedisDatabase())
                .compose(v -> dbService.init(vx, serverConfig.getMysqlHost(), serverConfig.getMysqlPort(),
                        serverConfig.getMysqlDatabase(), serverConfig.getMysqlUser(), serverConfig.getMysqlPassword()))
                .compose(v -> {
                    log.info("Storage services initialized");
                    return dbService.initSchema();
                })
                .compose(v -> {
                    try {
                        folkmqService.init(serverConfig.getFolkmqHost(), serverConfig.getFolkmqPort(), serverConfig.getFolkmqAppName());
                        log.info("Folkmq connected to {}:{}", serverConfig.getFolkmqHost(), serverConfig.getFolkmqPort());
                    } catch (java.io.IOException e) {
                        log.warn("Folkmq connection failed, falling back to EventBus: {}", e.getMessage());
                    }
                    PushServiceHolder.init(
                        new ApnsService(serverConfig.isApnsEnabled(), serverConfig.getApnsBundleId(), serverConfig.getApnsP8Path(), serverConfig.getApnsTeamId(), serverConfig.getApnsKeyId()),
                        new FcmService(serverConfig.isFcmEnabled(), serverConfig.getFcmCredentialsPath())
                    );
                    log.info("Push services initialized (APNs={}, FCM={})", serverConfig.isApnsEnabled(), serverConfig.isFcmEnabled());
                    return io.vertx.core.Future.succeededFuture();
                })
                .compose(v -> {
                    log.info("Database schema verified");
                    JsonObject verticleConfig = serverConfig.toJson();
                    DeploymentOptions depOpts = new DeploymentOptions().setConfig(verticleConfig);
                    return deployVerticles(vx, depOpts);
                })
                .onSuccess(v -> log.info("java-im server v0.4.0-sprint1 started"))
                .onFailure(err -> {
                    log.error("Server startup failed: {}", err.getMessage());
                    vx.close();
                });
    }

    private static io.vertx.core.Future<Void> deployVerticles(Vertx vx, DeploymentOptions depOpts) {
        return vx.deployVerticle("com.im.server.gateway.GatewayVerticle", depOpts)
                .compose(id -> { log.info("GatewayVerticle deployed: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vx.deployVerticle("com.im.server.gateway.HttpApiVerticle", depOpts))
                .compose(id -> { log.info("HttpApiVerticle deployed: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vx.deployVerticle("com.im.server.logic.AuthVerticle",
                        new DeploymentOptions().setConfig(depOpts.getConfig()).setInstances(2)))
                .compose(id -> { log.info("AuthVerticle deployed x2: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vx.deployVerticle("com.im.server.logic.C2CVerticle",
                        new DeploymentOptions().setConfig(depOpts.getConfig()).setInstances(2)))
                .compose(id -> { log.info("C2CVerticle deployed x2: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vx.deployVerticle("com.im.server.logic.GroupVerticle",
                        new DeploymentOptions().setConfig(depOpts.getConfig()).setInstances(2)))
                .compose(id -> { log.info("GroupVerticle deployed x2: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vx.deployVerticle("com.im.server.logic.SessionVerticle",
                        new DeploymentOptions().setConfig(depOpts.getConfig()).setInstances(2)))
                .compose(id -> { log.info("SessionVerticle deployed x2: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vx.deployVerticle("com.im.server.logic.PushVerticle",
                        new DeploymentOptions().setConfig(depOpts.getConfig()).setInstances(2)))
                .compose(id -> { log.info("PushVerticle deployed x2: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .compose(v -> vx.deployVerticle("com.im.server.logic.GroupPullVerticle", depOpts))
                .compose(id -> { log.info("GroupPullVerticle deployed: {}", id); return io.vertx.core.Future.succeededFuture(); })
                .mapEmpty();
    }

    private static void gracefulShutdown() {
        if (!shuttingDown.compareAndSet(false, true)) return;
        log.info("Graceful shutdown initiated (SIGTERM)");

        FolkmqService folkmq = FolkmqServiceHolder.getInstance();
        if (folkmq != null) {
            folkmq.close();
        }

        if (vertx != null) {
            vertx.close()
                    .onSuccess(v -> log.info("Vertx closed cleanly"))
                    .onFailure(e -> log.error("Vertx close error: {}", e.getMessage()));
        }
    }
}
