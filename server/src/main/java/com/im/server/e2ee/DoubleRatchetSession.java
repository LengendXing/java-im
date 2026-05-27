package com.im.server.e2ee;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;

public class DoubleRatchetSession {
    private static final String CURVE = "X25519";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int KEY_LEN = 32;
    private static final int MSG_KEY_SEED = 0x01;
    private static final int CHAIN_KEY_SEED = 0x02;
    private static final int MAX_SKIP = 1000;

    private byte[] rootKey;
    private byte[] sendChainKey;
    private byte[] recvChainKey;
    private int sendCount;
    private int recvCount;
    private int prevSendCount;
    private KeyPair dhPair;
    private PublicKey remoteDhPub;
    private Deque<SkippedKey> skippedKeys = new ArrayDeque<>();

    private static class SkippedKey {
        byte[] dhPubEncoded;
        int msgNum;
        byte[] msgKey;
    }

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
        return kpg.generateKeyPair();
    }

    public static byte[] sign(PrivateKey key, byte[] data) throws Exception {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(key);
        sig.update(data);
        return sig.sign();
    }

    public static boolean verify(PublicKey key, byte[] data, byte[] signature) throws Exception {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(key);
        sig.update(data);
        return sig.verify(signature);
    }

    public void initAsSender(KeyPair identityKey, KeyPair ephemeralKey,
                             PublicKey remoteIdentity, PublicKey remoteSignedPrekey,
                             byte[] remoteOtpk) throws Exception {
        // X3DH: compute shared secret
        byte[] dh1 = dh(identityKey.getPrivate(), remoteSignedPrekey);
        byte[] dh2 = dh(ephemeralKey.getPrivate(), remoteIdentity);
        byte[] dh3 = dh(ephemeralKey.getPrivate(), remoteSignedPrekey);
        byte[] dh4 = remoteOtpk != null ? dh(ephemeralKey.getPrivate(), bytesToPubKey(remoteOtpk)) : new byte[0];

        byte[] sharedSecret = concat(dh1, dh2, dh3, dh4);
        this.rootKey = hkdf(new byte[0], sharedSecret, "ImE2EE_X3DH");
        this.dhPair = ephemeralKey;
        this.remoteDhPub = remoteSignedPrekey;

        // First ratchet step
        byte[] dhOut = dh(dhPair.getPrivate(), remoteDhPub);
        byte[] out = hkdf(rootKey, dhOut, "ImE2EE_RatchetStep");
        ByteBuffer buf = ByteBuffer.wrap(out);
        this.rootKey = new byte[KEY_LEN];
        this.sendChainKey = new byte[KEY_LEN];
        buf.get(rootKey);
        buf.get(sendChainKey);
        this.recvChainKey = null;
        this.sendCount = 0;
        this.recvCount = 0;
    }

    public void initAsReceiver(KeyPair identityKey, KeyPair signedPrekey,
                               PrivateKey otpkPriv,
                               PublicKey remoteIdentity, PublicKey remoteEphemeral) throws Exception {
        // X3DH mirror
        byte[] dh1 = dh(signedPrekey.getPrivate(), remoteIdentity);
        byte[] dh2 = dh(identityKey.getPrivate(), remoteEphemeral);
        byte[] dh3 = dh(signedPrekey.getPrivate(), remoteEphemeral);
        byte[] dh4 = otpkPriv != null ? dh(otpkPriv, remoteEphemeral) : new byte[0];

        byte[] sharedSecret = concat(dh1, dh2, dh3, dh4);
        this.rootKey = hkdf(new byte[0], sharedSecret, "ImE2EE_X3DH");
        this.dhPair = signedPrekey;
        this.remoteDhPub = remoteEphemeral;

        byte[] dhOut = dh(dhPair.getPrivate(), remoteDhPub);
        byte[] out = hkdf(rootKey, dhOut, "ImE2EE_RatchetStep");
        ByteBuffer buf = ByteBuffer.wrap(out);
        this.rootKey = new byte[KEY_LEN];
        this.recvChainKey = new byte[KEY_LEN];
        buf.get(rootKey);
        buf.get(recvChainKey);
        this.sendChainKey = null;
        this.sendCount = 0;
        this.recvCount = 0;
    }

    public EncryptResult encrypt(byte[] plaintext) throws Exception {
        if (sendChainKey == null) {
            prevSendCount = sendCount;
            dhPair = generateKeyPair();
            byte[] dhOut = dh(dhPair.getPrivate(), remoteDhPub);
            byte[] out = hkdf(rootKey, dhOut, "ImE2EE_RatchetStep");
            ByteBuffer buf = ByteBuffer.wrap(out);
            rootKey = new byte[KEY_LEN];
            sendChainKey = new byte[KEY_LEN];
            buf.get(rootKey);
            buf.get(sendChainKey);
            sendCount = 0;
        }

        byte[] msgKey = hmac(sendChainKey, intToBytes(MSG_KEY_SEED));
        sendChainKey = hmac(sendChainKey, intToBytes(CHAIN_KEY_SEED));

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(msgKey, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        sendCount++;
        return new EncryptResult(ciphertext, iv, dhPair.getPublic().getEncoded(), sendCount - 1, prevSendCount);
    }

    public byte[] decrypt(byte[] ciphertext, byte[] iv, byte[] remoteDhPubEncoded, int msgNum, int prevChainLength) throws Exception {
        // 1. Check skipped keys first (out-of-order messages)
        Iterator<SkippedKey> it = skippedKeys.iterator();
        while (it.hasNext()) {
            SkippedKey sk = it.next();
            if (sk.msgNum == msgNum && Arrays.equals(sk.dhPubEncoded, remoteDhPubEncoded)) {
                it.remove();
                Cipher cipher = Cipher.getInstance(AES_GCM);
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sk.msgKey, "AES"), new GCMParameterSpec(128, iv));
                return cipher.doFinal(ciphertext);
            }
        }

        byte[] currentRemoteEncoded = remoteDhPub.getEncoded();

        if (!Arrays.equals(remoteDhPubEncoded, currentRemoteEncoded)) {
            // DH ratchet step
            // Step 0: Skip remaining messages in old receiving chain
            if (prevChainLength > recvCount) {
                skipMessages(prevChainLength);
            }

            // Step 1: receiving chain — use OLD dhPair
            PublicKey theirDh = bytesToPubKey(remoteDhPubEncoded);
            remoteDhPub = theirDh;
            byte[] dhOut1 = dh(dhPair.getPrivate(), remoteDhPub);
            byte[] out1 = hkdf(rootKey, dhOut1, "ImE2EE_RatchetStep");
            ByteBuffer buf1 = ByteBuffer.wrap(out1);
            rootKey = new byte[KEY_LEN];
            recvChainKey = new byte[KEY_LEN];
            buf1.get(rootKey);
            buf1.get(recvChainKey);
            recvCount = 0;

            // Step 2: sending chain — generate NEW dhPair
            dhPair = generateKeyPair();
            byte[] dhOut2 = dh(dhPair.getPrivate(), remoteDhPub);
            byte[] out2 = hkdf(rootKey, dhOut2, "ImE2EE_RatchetStep");
            ByteBuffer buf2 = ByteBuffer.wrap(out2);
            prevSendCount = sendCount;
            rootKey = new byte[KEY_LEN];
            sendChainKey = new byte[KEY_LEN];
            buf2.get(rootKey);
            buf2.get(sendChainKey);
            sendCount = 0;
        }

        // 2. Skip messages in current receiving chain
        skipMessages(msgNum);

        // 3. Derive message key and decrypt
        byte[] msgKey = hmac(recvChainKey, intToBytes(MSG_KEY_SEED));
        recvChainKey = hmac(recvChainKey, intToBytes(CHAIN_KEY_SEED));
        recvCount++;

        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(msgKey, "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(ciphertext);
    }

    private void skipMessages(int until) throws Exception {
        if (until - recvCount > MAX_SKIP) throw new SecurityException("too many skipped messages");
        while (recvCount < until) {
            byte[] msgKey = hmac(recvChainKey, intToBytes(MSG_KEY_SEED));
            recvChainKey = hmac(recvChainKey, intToBytes(CHAIN_KEY_SEED));
            SkippedKey sk = new SkippedKey();
            sk.dhPubEncoded = remoteDhPub.getEncoded();
            sk.msgNum = recvCount;
            sk.msgKey = msgKey;
            skippedKeys.addLast(sk);
            recvCount++;
        }
    }

    private byte[] dh(PrivateKey priv, PublicKey pub) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(priv);
        ka.doPhase(pub, true);
        return ka.generateSecret();
    }

    private byte[] hkdf(byte[] salt, byte[] input, String info) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        byte[] prk = salt.length > 0 ? salt : new byte[KEY_LEN];
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] t1 = mac.doFinal(input);

        mac.init(new SecretKeySpec(t1, "HmacSHA256"));
        byte[] okm1 = mac.doFinal(concat(info.getBytes(), new byte[]{0x01}));

        mac.init(new SecretKeySpec(t1, "HmacSHA256"));
        byte[] okm2 = mac.doFinal(concat(okm1, info.getBytes(), new byte[]{0x02}));

        ByteBuffer buf = ByteBuffer.wrap(concat(okm1, okm2));
        byte[] k1 = new byte[KEY_LEN];
        byte[] k2 = new byte[KEY_LEN];
        buf.get(k1);
        buf.get(k2);
        return concat(k1, k2);
    }

    private byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private PublicKey bytesToPubKey(byte[] encoded) throws Exception {
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("X25519");
        return kf.generatePublic(new java.security.spec.X509EncodedKeySpec(encoded));
    }

    private byte[] intToBytes(int v) { return ByteBuffer.allocate(4).putInt(v).array(); }

    private byte[] concat(byte[]... arrays) {
        int len = 0;
        for (byte[] a : arrays) len += a.length;
        ByteBuffer buf = ByteBuffer.allocate(len);
        for (byte[] a : arrays) buf.put(a);
        return buf.array();
    }

    public static class EncryptResult {
        public final byte[] ciphertext;
        public final byte[] iv;
        public final byte[] dhPubEncoded;
        public final int msgNum;
        public final int prevChainLength;

        public EncryptResult(byte[] ciphertext, byte[] iv, byte[] dhPubEncoded, int msgNum, int prevChainLength) {
            this.ciphertext = ciphertext;
            this.iv = iv;
            this.dhPubEncoded = dhPubEncoded;
            this.msgNum = msgNum;
            this.prevChainLength = prevChainLength;
        }
    }
}
