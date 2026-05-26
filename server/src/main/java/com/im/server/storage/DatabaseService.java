package com.im.server.storage;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.mysqlclient.MySQLClient;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DatabaseService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);
    private MySQLPool pool;

    public Future<Void> init(io.vertx.core.Vertx vertx, String host, int port, String database, String user, String password) {
        Promise<Void> promise = Promise.promise();

        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                .setHost(host)
                .setPort(port)
                .setDatabase(database)
                .setUser(user)
                .setPassword(password);

        PoolOptions poolOptions = new PoolOptions().setMaxSize(10);

        this.pool = MySQLPool.pool(vertx, connectOptions, poolOptions);

        // Test connection
        pool.getConnection()
                .onSuccess(conn -> {
                    conn.close();
                    log.info("MySQL connected: {}:{}/{}", host, port, database);
                    promise.complete();
                })
                .onFailure(err -> {
                    log.error("MySQL connect failed: {}", err.getMessage());
                    promise.fail(err);
                });

        return promise.future();
    }

    private long getLastInsertId(RowSet<Row> rows) {
        // Vert.x MySQL client returns auto-increment ID via the first column of the result
        // For INSERT operations, we need to query LAST_INSERT_ID()
        // As a workaround, we use a separate query
        return -1L; // Will be fetched via dedicated method
    }

    public Future<Long> createUser(String username, String passwordHash, String salt) {
        Promise<Long> promise = Promise.promise();
        pool.preparedQuery("INSERT INTO im_users (username, nickname, password_hash, salt, created_at) VALUES (?, ?, ?, ?, ?)")
                .execute(Tuple.of(username, username, passwordHash, salt, System.currentTimeMillis()))
                .onSuccess(rows -> {
                    long id = rows.property(MySQLClient.LAST_INSERTED_ID);
                    promise.complete(id);
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<User> getUserById(long userId) {
        Promise<User> promise = Promise.promise();
        pool.preparedQuery("SELECT user_id, username, nickname, avatar_url, password_hash, salt, created_at FROM im_users WHERE user_id = ?")
                .execute(Tuple.of(userId))
                .onSuccess(rows -> {
                    if (rows.iterator().hasNext()) {
                        promise.complete(rowToUser(rows.iterator().next()));
                    } else {
                        promise.complete(null);
                    }
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<User> authenticate(String username, String passwordHash) {
        Promise<User> promise = Promise.promise();
        pool.preparedQuery("SELECT user_id, username, nickname, avatar_url, password_hash, salt, created_at FROM im_users WHERE username = ? AND password_hash = ?")
                .execute(Tuple.of(username, passwordHash))
                .onSuccess(rows -> {
                    if (rows.iterator().hasNext()) {
                        promise.complete(rowToUser(rows.iterator().next()));
                    } else {
                        promise.complete(null);
                    }
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<User> getUserByUsername(String username) {
        Promise<User> promise = Promise.promise();
        pool.preparedQuery("SELECT user_id, username, nickname, avatar_url, password_hash, salt, created_at FROM im_users WHERE username = ?")
                .execute(Tuple.of(username))
                .onSuccess(rows -> {
                    if (rows.iterator().hasNext()) {
                        promise.complete(rowToUser(rows.iterator().next()));
                    } else {
                        promise.complete(null);
                    }
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> insertMessage(Message msg) {
        Promise<Void> promise = Promise.promise();
        pool.preparedQuery("INSERT INTO im_message (msg_id, session_id, sender_id, seq, content_type, content_text, content_url, content_extra, client_msg_id, server_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .execute(Tuple.of(msg.getMsgId(), msg.getSessionId(), msg.getSenderId(), msg.getSeq(),
                        msg.getContentType(), msg.getContentText(), msg.getContentUrl(), msg.getContentExtra(),
                        msg.getClientMsgId(), msg.getServerTime()))
                .onSuccess(rows -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<List<Message>> getMessages(String sessionId, long lastSeq, int limit) {
        Promise<List<Message>> promise = Promise.promise();
        pool.preparedQuery("SELECT msg_id, session_id, sender_id, seq, content_type, content_text, content_url, content_extra, client_msg_id, server_time FROM im_message WHERE session_id = ? AND seq > ? ORDER BY seq ASC LIMIT ?")
                .execute(Tuple.of(sessionId, lastSeq, limit))
                .onSuccess(rows -> {
                    List<Message> messages = new ArrayList<>();
                    for (Row row : rows) {
                        messages.add(rowToMessage(row));
                    }
                    promise.complete(messages);
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> upsertSession(long userId, String sessionId, int type, long targetId, String name, String lastMsg, long lastMsgTime) {
        Promise<Void> promise = Promise.promise();
        pool.preparedQuery("INSERT INTO im_session (user_id, session_id, type, target_id, name, last_msg, last_msg_time, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE last_msg = ?, last_msg_time = ?, updated_at = ?")
                .execute(Tuple.of(userId, sessionId, type, targetId, name, lastMsg, lastMsgTime, lastMsgTime,
                        lastMsg, lastMsgTime, lastMsgTime))
                .onSuccess(rows -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<List<Session>> getSessions(long userId, int limit) {
        Promise<List<Session>> promise = Promise.promise();
        pool.preparedQuery("SELECT id, user_id, session_id, type, target_id, name, avatar_url, last_msg, last_msg_time, unread_count, is_top, is_muted, updated_at FROM im_session WHERE user_id = ? ORDER BY updated_at DESC LIMIT ?")
                .execute(Tuple.of(userId, limit))
                .onSuccess(rows -> {
                    List<Session> sessions = new ArrayList<>();
                    for (Row row : rows) {
                        sessions.add(rowToSession(row));
                    }
                    promise.complete(sessions);
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> resetUnreadCount(long userId, String sessionId) {
        Promise<Void> promise = Promise.promise();
        pool.preparedQuery("UPDATE im_session SET unread_count = 0 WHERE user_id = ? AND session_id = ?")
                .execute(Tuple.of(userId, sessionId))
                .onSuccess(rows -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> incrUnreadCount(long userId, String sessionId) {
        Promise<Void> promise = Promise.promise();
        pool.preparedQuery("UPDATE im_session SET unread_count = unread_count + 1 WHERE user_id = ? AND session_id = ?")
                .execute(Tuple.of(userId, sessionId))
                .onSuccess(rows -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Long> createGroup(String name, long ownerId) {
        Promise<Long> promise = Promise.promise();
        pool.preparedQuery("INSERT INTO im_group (name, owner_id, created_at) VALUES (?, ?, ?)")
                .execute(Tuple.of(name, ownerId, System.currentTimeMillis()))
                .onSuccess(rows -> {
                    long id = rows.property(MySQLClient.LAST_INSERTED_ID);
                    promise.complete(id);
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> addGroupMember(long groupId, long userId, int role) {
        Promise<Void> promise = Promise.promise();
        pool.preparedQuery("INSERT INTO im_group_member (group_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)")
                .execute(Tuple.of(groupId, userId, role, System.currentTimeMillis()))
                .onSuccess(rows -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<List<Long>> getGroupMembers(long groupId) {
        Promise<List<Long>> promise = Promise.promise();
        pool.preparedQuery("SELECT user_id FROM im_group_member WHERE group_id = ?")
                .execute(Tuple.of(groupId))
                .onSuccess(rows -> {
                    List<Long> members = new ArrayList<>();
                    for (Row row : rows) {
                        members.add(row.getLong(0));
                    }
                    promise.complete(members);
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> addFriend(long userId, long targetUserId) {
        Promise<Void> promise = Promise.promise();
        pool.preparedQuery("INSERT IGNORE INTO im_friend (user_id, friend_id, created_at) VALUES (?, ?, ?)")
                .execute(Tuple.of(userId, targetUserId, System.currentTimeMillis()))
                .onSuccess(rows -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> addFriendRequest(long fromUserId, long toUserId, String message) {
        Promise<Void> promise = Promise.promise();
        pool.preparedQuery("INSERT INTO im_friend_request (from_user_id, to_user_id, message, status, created_at) VALUES (?, ?, ?, 0, ?)")
                .execute(Tuple.of(fromUserId, toUserId, message, System.currentTimeMillis()))
                .onSuccess(rows -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<List<User>> getFriendList(long userId, int limit) {
        Promise<List<User>> promise = Promise.promise();
        pool.preparedQuery("SELECT u.user_id, u.username, u.nickname, u.avatar_url FROM im_friend f JOIN im_users u ON f.friend_id = u.user_id WHERE f.user_id = ? ORDER BY f.created_at DESC LIMIT ?")
                .execute(Tuple.of(userId, limit))
                .onSuccess(rows -> {
                    List<User> friends = new ArrayList<>();
                    for (Row row : rows) {
                        User u = new User();
                        u.setUserId(row.getLong(0));
                        u.setUsername(row.getString(1));
                        u.setNickname(row.getString(2));
                        u.setAvatarUrl(row.getString(3));
                        friends.add(u);
                    }
                    promise.complete(friends);
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> initSchema() {
        Promise<Void> promise = Promise.promise();
        String sql = """
            CREATE TABLE IF NOT EXISTS im_users (
                user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(64) NOT NULL UNIQUE,
                nickname VARCHAR(64) NOT NULL,
                avatar_url VARCHAR(256) DEFAULT '',
                password_hash VARCHAR(128) NOT NULL,
                salt VARCHAR(64) NOT NULL,
                created_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            CREATE TABLE IF NOT EXISTS im_message (
                msg_id BIGINT PRIMARY KEY,
                session_id VARCHAR(64) NOT NULL,
                sender_id BIGINT NOT NULL,
                seq BIGINT NOT NULL,
                content_type INT NOT NULL DEFAULT 1,
                content_text TEXT,
                content_url VARCHAR(512) DEFAULT '',
                content_extra TEXT,
                client_msg_id VARCHAR(64) DEFAULT '',
                server_time BIGINT NOT NULL,
                INDEX idx_session_seq (session_id, seq)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            CREATE TABLE IF NOT EXISTS im_session (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                session_id VARCHAR(64) NOT NULL,
                type INT NOT NULL,
                target_id BIGINT NOT NULL,
                name VARCHAR(64) DEFAULT '',
                avatar_url VARCHAR(256) DEFAULT '',
                last_msg TEXT,
                last_msg_time BIGINT DEFAULT 0,
                unread_count INT DEFAULT 0,
                is_top TINYINT DEFAULT 0,
                is_muted TINYINT DEFAULT 0,
                updated_at BIGINT DEFAULT 0,
                UNIQUE KEY uk_user_session (user_id, session_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            CREATE TABLE IF NOT EXISTS im_group (
                group_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(64) NOT NULL,
                owner_id BIGINT NOT NULL,
                created_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            CREATE TABLE IF NOT EXISTS im_group_member (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                group_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                role INT NOT NULL DEFAULT 0,
                joined_at BIGINT NOT NULL,
                UNIQUE KEY uk_group_user (group_id, user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            CREATE TABLE IF NOT EXISTS im_friend (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                friend_id BIGINT NOT NULL,
                created_at BIGINT NOT NULL,
                UNIQUE KEY uk_user_friend (user_id, friend_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            CREATE TABLE IF NOT EXISTS im_friend_request (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                from_user_id BIGINT NOT NULL,
                to_user_id BIGINT NOT NULL,
                message VARCHAR(256) DEFAULT '',
                status INT NOT NULL DEFAULT 0,
                created_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;
        pool.query(sql).execute()
                .onSuccess(rows -> {
                    log.info("Database schema initialized");
                    promise.complete();
                })
                .onFailure(err -> {
                    log.error("Schema init failed: {}", err.getMessage());
                    promise.fail(err);
                });
        return promise.future();
    }

    private User rowToUser(Row row) {
        User u = new User();
        u.setUserId(row.getLong(0));
        u.setUsername(row.getString(1));
        u.setNickname(row.getString(2));
        u.setAvatarUrl(row.getString(3));
        u.setPasswordHash(row.getString(4));
        u.setSalt(row.getString(5));
        u.setCreatedAt(row.getLong(6));
        return u;
    }

    private Message rowToMessage(Row row) {
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
        return m;
    }

    private Session rowToSession(Row row) {
        Session s = new Session();
        s.setId(row.getLong(0));
        s.setUserId(row.getLong(1));
        s.setSessionId(row.getString(2));
        s.setType(row.getInteger(3));
        s.setTargetId(row.getLong(4));
        s.setName(row.getString(5));
        s.setAvatarUrl(row.getString(6));
        s.setLastMsg(row.getString(7));
        s.setLastMsgTime(row.getLong(8));
        s.setUnreadCount(row.getInteger(9));
        s.setTop(row.getInteger(10) != 0);
        s.setMuted(row.getInteger(11) != 0);
        s.setUpdatedAt(row.getLong(12));
        return s;
    }

    public MySQLPool getPool() {
        return pool;
    }
}
