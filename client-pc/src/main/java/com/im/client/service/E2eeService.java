package com.im.client.service;

import com.im.server.e2ee.DoubleRatchetSession;
import com.im.client.util.JwtUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.im.client.util.Config;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * E2EE encryption service for PC client.
 * Reuses the server module's DoubleRatchetSession implementation.
 */
public class E2eeService {

    private static final Logger log = LoggerFactory.getLogger(E2eeService.class);
    private static E2eeService instance;
    private final Gson gson = new Gson();
    private final Map<Long, DoubleRatchetSession> sessions = new ConcurrentHashMap<>();
    private KeyPair identityKey;

    private E2eeService() {}

    public static synchronized E2eeService getInstance() {
        if (instance == null) instance = new E2eeService();
        return instance;
    }

    public KeyPair getOrCreateIdentityKey() throws Exception {
        if (identityKey == null) identityKey = DoubleRatchetSession.generateKeyPair();
        return identityKey;
    }

    public void uploadPublicKey(KeyPair keyPair, int keyType, int keyId) {
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("keyType", keyType);
                body.addProperty("keyId", keyId);
                body.addProperty("publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
                AuthService.getInstance().httpPost(Config.getHttpBaseUrl() + "/api/e2ee/keys", body.toString());
                log.info("E2EE public key uploaded: type={}, id={}", keyType, keyId);
            } catch (Exception e) {
                log.error("Upload E2EE key failed: {}", e.getMessage());
            }
        }).start();
    }

    public DoubleRatchetSession.EncryptResult encrypt(long remoteUserId, byte[] plaintext) throws Exception {
        DoubleRatchetSession session = sessions.get(remoteUserId);
        if (session == null) {
            session = initAsSender(remoteUserId);
            sessions.put(remoteUserId, session);
        }
        return session.encrypt(plaintext);
    }

    public byte[] decrypt(long remoteUserId, byte[] ciphertext, byte[] iv, byte[] dhPubEncoded, int msgNum, int prevChainLength) throws Exception {
        DoubleRatchetSession session = sessions.get(remoteUserId);
        if (session == null) throw new IllegalStateException("No E2EE session for user " + remoteUserId);
        return session.decrypt(ciphertext, iv, dhPubEncoded, msgNum, prevChainLength);
    }

    private DoubleRatchetSession initAsSender(long remoteUserId) throws Exception {
        String resp = AuthService.getInstance().httpGet(Config.getHttpBaseUrl() + "/api/e2ee/keys/" + remoteUserId);
        if (resp == null) throw new RuntimeException("Failed to fetch key bundle for user " + remoteUserId);

        JsonObject json = gson.fromJson(resp, JsonObject.class);
        int code = json.has("code") ? json.get("code").getAsInt() : -1;
        if (code != 0) throw new RuntimeException("Key bundle fetch failed");

        var keys = json.getAsJsonArray("keys");
        byte[] identityPub = Base64.getDecoder().decode(keys.get(0).getAsString());
        byte[] signedPrekeyPub = Base64.getDecoder().decode(keys.get(1).getAsString());

        KeyPair identity = getOrCreateIdentityKey();
        KeyPair ephemeral = DoubleRatchetSession.generateKeyPair();

        DoubleRatchetSession session = new DoubleRatchetSession();
        session.initAsSender(identity, ephemeral,
                java.security.KeyFactory.getInstance("X25519").generatePublic(new java.security.spec.X509EncodedKeySpec(identityPub)),
                java.security.KeyFactory.getInstance("X25519").generatePublic(new java.security.spec.X509EncodedKeySpec(signedPrekeyPub)),
                null);
        return session;
    }

    public boolean hasSession(long remoteUserId) {
        return sessions.containsKey(remoteUserId);
    }
}
