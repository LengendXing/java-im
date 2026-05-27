package com.im.server.gateway;

import com.im.server.common.RedisServiceHolder;
import com.im.server.common.ServerConfig;
import com.im.server.e2ee.E2eeKeyServiceHolder;
import com.im.server.common.DatabaseServiceHolder;
import com.im.server.storage.User;
import com.im.server.storage.RedisService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HttpApiVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(HttpApiVerticle.class);
    private static final String UPLOAD_DIR = System.getProperty("dufs.upload.dir", "/root/java-im/uploads");
    private static final String DUFS_URL = System.getProperty("dufs.url", "http://127.0.0.1:5100");

    private static final Set<HttpMethod> ALLOWED_METHODS = Set.of(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS
    );
    private static final Set<String> ALLOWED_HEADERS = Set.of(
            "Content-Type", "Authorization", "X-Requested-With", "Accept", "Origin"
    );

    @Override
    public void start() {
        JsonObject cfg = config();
        ServerConfig serverConfig = ServerConfig.fromJson(cfg);
        int httpPort = serverConfig.getHttpPort();
        RedisService redisService = RedisServiceHolder.getInstance();

        Router router = Router.router(vertx);

        router.route().handler(CorsHandler.create()
                .addRelativeOrigins(List.of(".*"))
                .allowedMethods(ALLOWED_METHODS)
                .allowedHeaders(ALLOWED_HEADERS)
                .allowCredentials(true));

        router.route().handler(BodyHandler.create()
                .setUploadsDirectory(UPLOAD_DIR)
                .setBodyLimit(50 * 1024 * 1024)); // 50MB max

        // Health check
        router.get("/health").handler(ctx -> {
            ctx.json(new JsonObject().put("status", "ok").put("version", "0.2.0"));
        });

        // Metrics endpoint for Prometheus
        router.get("/metrics").handler(ctx -> {
            String metrics = buildMetrics();
            ctx.response().putHeader("Content-Type", "text/plain").end(metrics);
        });

        // Auth middleware
        router.route("/api/*").handler(ctx -> {
            String path = ctx.request().path();
            if (path.endsWith("/login") || path.endsWith("/register")) {
                ctx.next();
                return;
            }
            String authHeader = ctx.request().getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                ctx.response().setStatusCode(401).end("{\"code\":1001,\"msg\":\"missing token\"}");
                return;
            }
            String token = authHeader.substring(7);
            redisService.validateToken(token)
                    .onSuccess(userId -> {
                        if (userId <= 0) {
                            ctx.response().setStatusCode(401).end("{\"code\":1001,\"msg\":\"invalid token\"}");
                        } else {
                            ctx.put("userId", userId);
                            ctx.next();
                        }
                    })
                    .onFailure(err ->
                            ctx.response().setStatusCode(401).end("{\"code\":1001,\"msg\":\"token validation failed\"}"));
        });

        // POST /api/register
        router.post("/api/register").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) { ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}"); return; }
            vertx.eventBus().<JsonObject>request("im.logic.REGISTER", body, reply -> handleReply(ctx, reply));
        });

        // POST /api/login
        router.post("/api/login").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) { ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}"); return; }
            vertx.eventBus().<JsonObject>request("im.logic.LOGIN", body, reply -> handleReply(ctx, reply));
        });

        // GET /api/user/search?q=xxx
        router.get("/api/user/search").handler(ctx -> {
            String q = ctx.request().getParam("q", "");
            if (q.trim().isEmpty()) {
                ctx.json(new JsonObject().put("code", 0).put("msg", "ok").put("data", new JsonArray()));
                return;
            }
            int limit = Integer.parseInt(ctx.request().getParam("limit", "20"));
            DatabaseServiceHolder.getInstance().searchUsers(q.trim(), limit)
                    .onSuccess(users -> {
                        JsonArray arr = new JsonArray();
                        for (var u : users) {
                            arr.add(new JsonObject()
                                    .put("userId", u.getUserId())
                                    .put("username", u.getUsername())
                                    .put("nickname", u.getNickname())
                                    .put("avatarUrl", u.getAvatarUrl()));
                        }
                        ctx.json(new JsonObject().put("code", 0).put("msg", "ok").put("data", arr));
                    })
                    .onFailure(err -> ctx.json(new JsonObject().put("code", 1).put("msg", "search failed")));
        });

        // GET /api/user/{id}
        router.get("/api/user/:id").handler(ctx -> {
            long userId;
            try { userId = Long.parseLong(ctx.pathParam("id")); } catch (NumberFormatException e) {
                ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid user id\"}"); return;
            }
            vertx.eventBus().<JsonObject>request("im.logic.USER_INFO", new JsonObject().put("userId", userId),
                    reply -> handleReply(ctx, reply));
        });

        // === Friend APIs ===

        // POST /api/friend/apply
        router.post("/api/friend/apply").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) { ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}"); return; }
            body.put("userId", ctx.get("userId"));
            vertx.eventBus().<JsonObject>request("im.logic.FRIEND_APPLY", body, reply -> handleReply(ctx, reply));
        });

        // POST /api/friend/accept
        router.post("/api/friend/accept").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) { ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}"); return; }
            body.put("userId", ctx.get("userId"));
            vertx.eventBus().<JsonObject>request("im.logic.FRIEND_ACCEPT", body, reply -> handleReply(ctx, reply));
        });

        // POST /api/friend/reject
        router.post("/api/friend/reject").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) { ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}"); return; }
            body.put("userId", ctx.get("userId"));
            vertx.eventBus().<JsonObject>request("im.logic.FRIEND_REJECT", body, reply -> handleReply(ctx, reply));
        });

        // GET /api/friend/list
        router.get("/api/friend/list").handler(ctx -> {
            long userId = ctx.get("userId");
            int limit = Integer.parseInt(ctx.request().getParam("limit", "50"));
            vertx.eventBus().<JsonObject>request("im.logic.FRIEND_LIST",
                    new JsonObject().put("userId", userId).put("limit", limit),
                    reply -> handleReply(ctx, reply));
        });

        // GET /api/friend/requests
        router.get("/api/friend/requests").handler(ctx -> {
            long userId = ctx.get("userId");
            vertx.eventBus().<JsonObject>request("im.logic.FRIEND_REQUEST_LIST",
                    new JsonObject().put("userId", userId),
                    reply -> handleReply(ctx, reply));
        });

        // === Group APIs ===

        // POST /api/group/create
        router.post("/api/group/create").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) { ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}"); return; }
            body.put("userId", ctx.get("userId"));
            vertx.eventBus().<JsonObject>request("im.logic.GROUP_CREATE", body, reply -> handleReply(ctx, reply));
        });

        // POST /api/group/invite
        router.post("/api/group/invite").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) { ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}"); return; }
            body.put("userId", ctx.get("userId"));
            vertx.eventBus().<JsonObject>request("im.logic.GROUP_INVITE", body, reply -> handleReply(ctx, reply));
        });

        // POST /api/group/kick
        router.post("/api/group/kick").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) { ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}"); return; }
            body.put("userId", ctx.get("userId"));
            vertx.eventBus().<JsonObject>request("im.logic.GROUP_KICK", body, reply -> handleReply(ctx, reply));
        });

        // POST /api/group/dissolve
        router.post("/api/group/dissolve").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) { ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}"); return; }
            body.put("userId", ctx.get("userId"));
            vertx.eventBus().<JsonObject>request("im.logic.GROUP_DISSOLVE", body, reply -> handleReply(ctx, reply));
        });

        // GET /api/group/:id/members
        router.get("/api/group/:id/members").handler(ctx -> {
            long groupId;
            try { groupId = Long.parseLong(ctx.pathParam("id")); } catch (NumberFormatException e) {
                ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid group id\"}"); return;
            }
            vertx.eventBus().<JsonObject>request("im.logic.GROUP_MEMBER_LIST",
                    new JsonObject().put("groupId", groupId), reply -> handleReply(ctx, reply));
        });

        // === Message APIs ===

        // POST /api/message/recall
        router.post("/api/message/recall").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) { ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}"); return; }
            body.put("userId", ctx.get("userId"));
            vertx.eventBus().<JsonObject>request("im.logic.MSG_RECALL", body, reply -> handleReply(ctx, reply));
        });

        // POST /api/message/read
        router.post("/api/message/read").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) { ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}"); return; }
            body.put("userId", ctx.get("userId"));
            vertx.eventBus().<JsonObject>request("im.logic.MSG_READ", body, reply -> handleReply(ctx, reply));
        });

        // === File Upload ===

        // POST /api/file/upload
        router.post("/api/file/upload").handler(ctx -> {
            FileUpload upload = ctx.fileUploads().isEmpty() ? null : ctx.fileUploads().get(0);
            if (upload == null) {
                ctx.json(new JsonObject().put("code", 1003).put("msg", "no file uploaded"));
                return;
            }

            String originalName = upload.fileName();
            String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : "";
            String storedName = UUID.randomUUID().toString() + ext;
            Path source = Path.of(upload.uploadedFileName());
            Path target = Path.of(UPLOAD_DIR, storedName);

            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                String url = DUFS_URL + "/" + storedName;
                ctx.json(new JsonObject()
                        .put("code", 0)
                        .put("msg", "ok")
                        .put("url", url)
                        .put("fileName", originalName)
                        .put("fileSize", upload.size()));
            } catch (IOException e) {
                log.error("File move failed: {}", e.getMessage());
                ctx.json(new JsonObject().put("code", 1).put("msg", "file save failed"));
            }
        });

        // === Search ===

        // GET /api/message/search?q=keyword&sessionId=xxx&limit=20
        router.get("/api/message/search").handler(ctx -> {
            String keyword = ctx.request().getParam("q", "");
            String sessionId = ctx.request().getParam("sessionId", "");
            int limit = Integer.parseInt(ctx.request().getParam("limit", "20"));
            long userId = ctx.get("userId");
            vertx.eventBus().<JsonObject>request("im.logic.MSG_SEARCH",
                    new JsonObject().put("userId", userId).put("keyword", keyword)
                            .put("sessionId", sessionId).put("limit", limit),
                    reply -> handleReply(ctx, reply));
        });

        // Push token registration
        router.post("/api/push/token").handler(ctx -> {
            long userId = ctx.get("userId");
            JsonObject body = ctx.body().asJsonObject();
            int platform = body.getInteger("platform", 0);
            String token = body.getString("token", "");
            String bundleId = body.getString("bundleId", "");
            if (platform <= 0 || token.isEmpty()) {
                ctx.json(new JsonObject().put("code", 1003).put("msg", "invalid params"));
                return;
            }
            DatabaseServiceHolder.getInstance().upsertPushToken(userId, platform, token, bundleId)
                    .onSuccess(v -> ctx.json(new JsonObject().put("code", 0).put("msg", "ok")))
                    .onFailure(e -> ctx.json(new JsonObject().put("code", 1).put("msg", e.getMessage())));
        });

        // E2EE key bundle upload
        router.post("/api/e2ee/keys").handler(ctx -> {
            long userId = ctx.get("userId");
            JsonObject body = ctx.body().asJsonObject();
            int keyType = body.getInteger("keyType", 0);
            int keyId = body.getInteger("keyId", 0);
            String publicKeyB64 = body.getString("publicKey", "");
            String signatureB64 = body.getString("signature", "");
            if (keyType <= 0 || keyId < 0 || publicKeyB64.isEmpty()) {
                ctx.json(new JsonObject().put("code", 1003).put("msg", "invalid params"));
                return;
            }
            byte[] publicKey = java.util.Base64.getDecoder().decode(publicKeyB64);
            byte[] signature = signatureB64.isEmpty() ? new byte[0] : java.util.Base64.getDecoder().decode(signatureB64);
            E2eeKeyServiceHolder.getInstance().storePublicKey(userId, keyType, keyId, publicKey, signature)
                    .onSuccess(v -> ctx.json(new JsonObject().put("code", 0).put("msg", "ok")))
                    .onFailure(e -> ctx.json(new JsonObject().put("code", 1).put("msg", e.getMessage())));
        });

        // E2EE key bundle fetch
        router.get("/api/e2ee/keys/:userId").handler(ctx -> {
            long targetUserId = Long.parseLong(ctx.pathParam("userId"));
            E2eeKeyServiceHolder.getInstance().getKeyBundle(targetUserId)
                    .onSuccess(keys -> {
                        JsonArray arr = new JsonArray();
                        for (byte[] k : keys) arr.add(java.util.Base64.getEncoder().encodeToString(k));
                        ctx.json(new JsonObject().put("code", 0).put("msg", "ok").put("keys", arr));
                    })
                    .onFailure(e -> ctx.json(new JsonObject().put("code", 1).put("msg", e.getMessage())));
        });

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(httpPort, res -> {
                    if (res.succeeded()) {
                        log.info("HTTP API listening on port {}", httpPort);
                    } else {
                        log.error("HTTP API listen failed: {}", res.cause().getMessage());
                    }
                });
    }

    private void handleReply(io.vertx.ext.web.RoutingContext ctx, io.vertx.core.AsyncResult<io.vertx.core.eventbus.Message<JsonObject>> reply) {
        if (reply.succeeded()) {
            ctx.json(reply.result().body());
        } else {
            ctx.response().setStatusCode(500).end("{\"code\":1,\"msg\":\"" + reply.cause().getMessage() + "\"}");
        }
    }

    private String buildMetrics() {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        return "# HELP im_server_info Server info\n"
                + "# TYPE im_server_info gauge\n"
                + "im_server_info{version=\"0.2.0\"} 1\n"
                + "# HELP im_jvm_memory_used_mb JVM memory used MB\n"
                + "# TYPE im_jvm_memory_used_mb gauge\n"
                + "im_jvm_memory_used_mb " + usedMB + "\n"
                + "# HELP im_connections_active Active TCP connections\n"
                + "# TYPE im_connections_active gauge\n"
                + "im_connections_active 0\n";
    }
}
