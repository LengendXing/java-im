package com.im.server.storage;

import io.vertx.core.Vertx;
import io.vertx.mysqlclient.MySQLClient;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class MigrationTool {
    private static final Logger log = LoggerFactory.getLogger(MigrationTool.class);
    private static final int BATCH_SIZE = 500;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneId.of("UTC"));

    private final MySQLPool pool;
    private final boolean dryRun;

    public MigrationTool(Vertx vertx, String host, int port, String database, String user, String password, boolean dryRun) {
        MySQLConnectOptions opts = new MySQLConnectOptions()
                .setHost(host).setPort(port).setDatabase(database).setUser(user).setPassword(password);
        this.pool = MySQLPool.pool(vertx, opts, new PoolOptions().setMaxSize(3));
        this.dryRun = dryRun;
    }

    public void run() {
        log.info("Migration started (dryRun={})", dryRun);
        ensureMonthlyTables()
                .compose(v -> migrateBatch(0))
                .onSuccess(v -> {
                    log.info("Migration completed (dryRun={})", dryRun);
                    pool.close();
                })
                .onFailure(err -> {
                    log.error("Migration failed: {}", err.getMessage());
                    pool.close();
                });
    }

    private io.vertx.core.Future<Void> ensureMonthlyTables() {
        return pool.query("SELECT MIN(server_time) AS min_ts, MAX(server_time) AS max_ts FROM im_message")
                .execute()
                .compose(rows -> {
                    if (!rows.iterator().hasNext()) return io.vertx.core.Future.succeededFuture();
                    Row row = rows.iterator().next();
                    Long minTs = row.getLong(0);
                    Long maxTs = row.getLong(1);
                    if (minTs == null || maxTs == null) {
                        log.info("No data in im_message, nothing to migrate");
                        return io.vertx.core.Future.succeededFuture();
                    }

                    List<String> tables = ShardingService.getTableNamesInRange(minTs, maxTs);
                    io.vertx.core.Future<Void> chain = io.vertx.core.Future.succeededFuture();
                    for (String table : tables) {
                        chain = chain.compose(v -> {
                            io.vertx.core.Promise<Void> p = io.vertx.core.Promise.promise();
                            pool.query(ShardingService.getCreateMonthlyTableSQL(table)).execute()
                                    .onSuccess(r -> { log.info("Ensured table {}", table); p.complete(); })
                                    .onFailure(err -> {
                                        if (err.getMessage() != null && err.getMessage().contains("already exists")) p.complete();
                                        else p.fail(err);
                                    });
                            return p.future();
                        });
                    }
                    return chain;
                });
    }

    private io.vertx.core.Future<Void> migrateBatch(long offset) {
        return pool.preparedQuery("SELECT msg_id, session_id, sender_id, seq, content_type, content_text, content_url, content_extra, client_msg_id, server_time FROM im_message ORDER BY server_time ASC LIMIT ? OFFSET ?")
                .execute(Tuple.of(BATCH_SIZE, offset))
                .compose(rows -> {
                    List<Message> messages = new ArrayList<>();
                    for (Row row : rows) {
                        Message m = new Message();
                        m.setMsgId(row.getLong(0));
                        m.setSessionId(row.getString(1));
                        m.setSenderId(row.getLong(2));
                        m.setSeq(row.getLong(3));
                        m.setContentType(row.getInteger(4));
                        m.setContentText(row.getString(5));
                        m.setContentUrl(row.getString(6));
                        m.setContentExtra(row.getString(7));
                        m.setClientMsgId(row.getString(8));
                        m.setServerTime(row.getLong(9));
                        messages.add(m);
                    }

                    if (messages.isEmpty()) return io.vertx.core.Future.succeededFuture();

                    if (dryRun) {
                        AtomicLong count = new AtomicLong(0);
                        messages.stream()
                                .collect(java.util.stream.Collectors.groupingBy(m -> ShardingService.getMonthlyTableName(m.getServerTime())))
                                .forEach((table, msgs) -> {
                                    count.addAndGet(msgs.size());
                                    log.info("[DRY-RUN] Would migrate {} rows to {}", msgs.size(), table);
                                });
                        log.info("[DRY-RUN] Batch offset={} count={}", offset, count.get());
                        return migrateBatch(offset + BATCH_SIZE);
                    }

                    io.vertx.core.Future<Void> chain = io.vertx.core.Future.succeededFuture();
                    for (Message m : messages) {
                        chain = chain.compose(v -> insertToMonthlyTable(m));
                    }
                    log.info("Migrated batch offset={} count={}", offset, messages.size());
                    return chain.compose(v -> migrateBatch(offset + BATCH_SIZE));
                });
    }

    private io.vertx.core.Future<Void> insertToMonthlyTable(Message msg) {
        String tableName = ShardingService.getMonthlyTableName(msg.getServerTime());
        io.vertx.core.Promise<Void> promise = io.vertx.core.Promise.promise();
        pool.preparedQuery("INSERT IGNORE INTO " + tableName + " (msg_id, session_id, sender_id, seq, content_type, content_text, content_url, content_extra, client_msg_id, server_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .execute(Tuple.of(msg.getMsgId(), msg.getSessionId(), msg.getSenderId(), msg.getSeq(),
                        msg.getContentType(), msg.getContentText(), msg.getContentUrl(), msg.getContentExtra(),
                        msg.getClientMsgId(), msg.getServerTime()))
                .onSuccess(v -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    public static void main(String[] args) {
        boolean dryRun = args.length > 0 && "--dry-run".equals(args[0]);
        String host = System.getProperty("IM_MYSQL_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("IM_MYSQL_PORT", "3306"));
        String database = System.getProperty("IM_MYSQL_DB", "im_db");
        String user = System.getProperty("IM_MYSQL_USER", "root");
        String password = System.getProperty("IM_MYSQL_PASSWORD", "root");

        Vertx vertx = Vertx.vertx();
        MigrationTool tool = new MigrationTool(vertx, host, port, database, user, password, dryRun);
        tool.run();
    }
}
