package com.im.server.gateway;

import com.im.server.common.RedisServiceHolder;
import com.im.server.common.ServerConfig;
import com.im.server.storage.RedisService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class HttpApiVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(HttpApiVerticle.class);

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

        // CORS handler
        router.route().handler(CorsHandler.create()
                .addRelativeOrigins(List.of(".*"))
                .allowedMethods(ALLOWED_METHODS)
                .allowedHeaders(ALLOWED_HEADERS)
                .allowCredentials(true));

        router.route().handler(BodyHandler.create());

        // Health check (no auth required)
        router.get("/health").handler(ctx -> {
            ctx.json(new JsonObject().put("status", "ok").put("version", "0.1.0"));
        });

        // Auth middleware for /api/* routes (except login/register)
        router.route("/api/*").handler(ctx -> {
            String path = ctx.request().path();
            // Skip auth for login and register
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
                    .onFailure(err -> {
                        ctx.response().setStatusCode(401).end("{\"code\":1001,\"msg\":\"token validation failed\"}");
                    });
        });

        // POST /api/register
        router.post("/api/register").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}");
                return;
            }
            vertx.eventBus().<JsonObject>request("im.logic.REGISTER", body, reply -> {
                if (reply.succeeded()) {
                    ctx.json(reply.result().body());
                } else {
                    ctx.response().setStatusCode(500).end("{\"code\":1,\"msg\":\"" + reply.cause().getMessage() + "\"}");
                }
            });
        });

        // POST /api/login
        router.post("/api/login").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}");
                return;
            }
            vertx.eventBus().<JsonObject>request("im.logic.LOGIN", body, reply -> {
                if (reply.succeeded()) {
                    ctx.json(reply.result().body());
                } else {
                    ctx.response().setStatusCode(500).end("{\"code\":1,\"msg\":\"" + reply.cause().getMessage() + "\"}");
                }
            });
        });

        // GET /api/user/{id}
        router.get("/api/user/:id").handler(ctx -> {
            String idStr = ctx.pathParam("id");
            long userId;
            try {
                userId = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid user id\"}");
                return;
            }
            JsonObject req = new JsonObject().put("userId", userId);
            vertx.eventBus().<JsonObject>request("im.logic.USER_INFO", req, reply -> {
                if (reply.succeeded()) {
                    ctx.json(reply.result().body());
                } else {
                    ctx.response().setStatusCode(500).end("{\"code\":1,\"msg\":\"db error\"}");
                }
            });
        });

        // POST /api/friend/apply
        router.post("/api/friend/apply").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}");
                return;
            }
            // Inject authenticated userId
            body.put("userId", ctx.get("userId"));
            vertx.eventBus().<JsonObject>request("im.logic.FRIEND_APPLY", body, reply -> {
                if (reply.succeeded()) {
                    ctx.json(reply.result().body());
                } else {
                    ctx.response().setStatusCode(500).end("{\"code\":1,\"msg\":\"" + reply.cause().getMessage() + "\"}");
                }
            });
        });

        // GET /api/friend/list
        router.get("/api/friend/list").handler(ctx -> {
            long userId = ctx.get("userId");
            int limit = Integer.parseInt(ctx.request().getParam("limit", "50"));
            JsonObject req = new JsonObject().put("userId", userId).put("limit", limit);
            vertx.eventBus().<JsonObject>request("im.logic.FRIEND_LIST", req, reply -> {
                if (reply.succeeded()) {
                    ctx.json(reply.result().body());
                } else {
                    ctx.response().setStatusCode(500).end("{\"code\":1,\"msg\":\"" + reply.cause().getMessage() + "\"}");
                }
            });
        });

        // POST /api/group/create
        router.post("/api/group/create").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid body\"}");
                return;
            }
            body.put("userId", ctx.get("userId"));
            vertx.eventBus().<JsonObject>request("im.logic.GROUP_CREATE", body, reply -> {
                if (reply.succeeded()) {
                    ctx.json(reply.result().body());
                } else {
                    ctx.response().setStatusCode(500).end("{\"code\":1,\"msg\":\"" + reply.cause().getMessage() + "\"}");
                }
            });
        });

        // GET /api/group/{id}/members
        router.get("/api/group/:id/members").handler(ctx -> {
            String idStr = ctx.pathParam("id");
            long groupId;
            try {
                groupId = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                ctx.response().setStatusCode(400).end("{\"code\":1003,\"msg\":\"invalid group id\"}");
                return;
            }
            JsonObject req = new JsonObject().put("groupId", groupId);
            vertx.eventBus().<JsonObject>request("im.logic.GROUP_MEMBER_LIST", req, reply -> {
                if (reply.succeeded()) {
                    ctx.json(reply.result().body());
                } else {
                    ctx.response().setStatusCode(500).end("{\"code\":1,\"msg\":\"" + reply.cause().getMessage() + "\"}");
                }
            });
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
}
