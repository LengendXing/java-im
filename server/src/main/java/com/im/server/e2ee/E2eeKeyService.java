package com.im.server.e2ee;

import com.im.server.storage.DatabaseService;
import com.im.server.common.DatabaseServiceHolder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class E2eeKeyService {
    private static final Logger log = LoggerFactory.getLogger(E2eeKeyService.class);

    public Future<Void> storePublicKey(long userId, int keyType, int keyId, byte[] publicKey, byte[] signature) {
        DatabaseService db = DatabaseServiceHolder.getInstance();
        Promise<Void> promise = Promise.promise();
        db.getPool().preparedQuery("INSERT INTO im_user_key (user_id, key_type, key_id, public_key, signature, created_at) VALUES (?, ?, ?, ?, ?, ?)")
                .execute(Tuple.of(userId, keyType, keyId, publicKey, signature, System.currentTimeMillis()))
                .onSuccess(v -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<List<byte[]>> getKeyBundle(long userId) {
        DatabaseService db = DatabaseServiceHolder.getInstance();
        Promise<List<byte[]>> promise = Promise.promise();
        db.getPool().preparedQuery("SELECT key_type, key_id, public_key, signature FROM im_user_key WHERE user_id = ? AND (key_type = 1 OR key_type = 2 OR (key_type = 3 AND used = 0)) ORDER BY key_type, key_id")
                .execute(Tuple.of(userId))
                .onSuccess(rows -> {
                    List<byte[]> keys = new ArrayList<>();
                    for (Row row : rows) {
                        // Pack: keyType(1) + keyId(4) + publicKey + signature
                        byte[] pubKey = row.getBuffer("public_key") != null ? row.getBuffer("public_key").getBytes() : new byte[0];
                        byte[] sig = row.getBuffer("signature") != null ? row.getBuffer("signature").getBytes() : new byte[0];
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        try {
                            baos.write(row.getInteger("key_type"));
                            baos.write(java.nio.ByteBuffer.allocate(4).putInt(row.getInteger("key_id")).array());
                            baos.write(pubKey);
                            baos.write(sig);
                        } catch (Exception ignored) {}
                        keys.add(baos.toByteArray());
                    }
                    promise.complete(keys);
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<Void> markOneTimePreKeyUsed(long userId, int keyId) {
        DatabaseService db = DatabaseServiceHolder.getInstance();
        Promise<Void> promise = Promise.promise();
        db.getPool().preparedQuery("UPDATE im_user_key SET used = 1 WHERE user_id = ? AND key_type = 3 AND key_id = ?")
                .execute(Tuple.of(userId, keyId))
                .onSuccess(v -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }
}
