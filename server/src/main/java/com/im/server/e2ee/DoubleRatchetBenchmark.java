package com.im.server.e2ee;

import java.security.KeyPair;
import java.util.Random;

public class DoubleRatchetBenchmark {

    private static final int WARMUP = 500;
    private static final int ITERATIONS = 5000;
    private static final int[] PAYLOAD_SIZES = {64, 256, 1024, 4096, 65536};

    public static void main(String[] args) throws Exception {
        System.out.println("=== DoubleRatchetSession Benchmark ===\n");

        benchmarkKeyGeneration();
        benchmarkX3DH();
        benchmarkEncryptDecrypt();
        benchmarkVariousPayloads();
        benchmarkDHRatchetStep();
        benchmarkOutOfOrder();
    }

    private static void benchmarkKeyGeneration() throws Exception {
        System.out.println("--- Key Generation ---");
        // Warmup
        for (int i = 0; i < WARMUP; i++) DoubleRatchetSession.generateKeyPair();

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) DoubleRatchetSession.generateKeyPair();
        long elapsed = System.nanoTime() - start;
        double opsPerSec = ITERATIONS * 1_000_000_000.0 / elapsed;
        System.out.printf("  X25519 key pair: %.0f ops/sec (%.2f µs/op)%n", opsPerSec, elapsed / (ITERATIONS * 1000.0));
        System.out.println();
    }

    private static void benchmarkX3DH() throws Exception {
        System.out.println("--- X3DH Initialization ---");
        // Warmup
        for (int i = 0; i < WARMUP / 10; i++) {
            KeyPair aliceId = DoubleRatchetSession.generateKeyPair();
            KeyPair aliceEph = DoubleRatchetSession.generateKeyPair();
            KeyPair bobId = DoubleRatchetSession.generateKeyPair();
            KeyPair bobSpk = DoubleRatchetSession.generateKeyPair();
            KeyPair bobOtpk = DoubleRatchetSession.generateKeyPair();

            DoubleRatchetSession alice = new DoubleRatchetSession();
            alice.initAsSender(aliceId, aliceEph, bobId.getPublic(), bobSpk.getPublic(), bobOtpk.getPublic().getEncoded());

            DoubleRatchetSession bob = new DoubleRatchetSession();
            bob.initAsReceiver(bobId, bobSpk, bobOtpk.getPrivate(), aliceId.getPublic(), aliceEph.getPublic());
        }

        int iters = ITERATIONS / 10;
        long start = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            KeyPair aliceId = DoubleRatchetSession.generateKeyPair();
            KeyPair aliceEph = DoubleRatchetSession.generateKeyPair();
            KeyPair bobId = DoubleRatchetSession.generateKeyPair();
            KeyPair bobSpk = DoubleRatchetSession.generateKeyPair();
            KeyPair bobOtpk = DoubleRatchetSession.generateKeyPair();

            DoubleRatchetSession alice = new DoubleRatchetSession();
            alice.initAsSender(aliceId, aliceEph, bobId.getPublic(), bobSpk.getPublic(), bobOtpk.getPublic().getEncoded());

            DoubleRatchetSession bob = new DoubleRatchetSession();
            bob.initAsReceiver(bobId, bobSpk, bobOtpk.getPrivate(), aliceId.getPublic(), aliceEph.getPublic());
        }
        long elapsed = System.nanoTime() - start;
        double opsPerSec = iters * 1_000_000_000.0 / elapsed;
        System.out.printf("  Full X3DH (sender+receiver): %.0f ops/sec (%.2f ms/op)%n", opsPerSec, elapsed / (iters * 1_000_000.0));
        System.out.println();
    }

    private static void benchmarkEncryptDecrypt() throws Exception {
        System.out.println("--- Encrypt/Decrypt (256-byte payload, same chain) ---");
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();
        initSession(alice, bob);

        byte[] payload = new byte[256];
        new Random().nextBytes(payload);

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            DoubleRatchetSession.EncryptResult enc = alice.encrypt(payload);
            bob.decrypt(enc.ciphertext, enc.iv, enc.dhPubEncoded, enc.msgNum, enc.prevChainLength);
        }

        // Re-init for fresh state
        alice = new DoubleRatchetSession();
        bob = new DoubleRatchetSession();
        initSession(alice, bob);

        // Encrypt benchmark
        long encStart = System.nanoTime();
        DoubleRatchetSession.EncryptResult[] encResults = new DoubleRatchetSession.EncryptResult[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            encResults[i] = alice.encrypt(payload);
        }
        long encElapsed = System.nanoTime() - encStart;
        double encOps = ITERATIONS * 1_000_000_000.0 / encElapsed;
        System.out.printf("  Encrypt: %.0f ops/sec (%.2f µs/op)%n", encOps, encElapsed / (ITERATIONS * 1000.0));

        // Decrypt benchmark
        long decStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            DoubleRatchetSession.EncryptResult enc = encResults[i];
            bob.decrypt(enc.ciphertext, enc.iv, enc.dhPubEncoded, enc.msgNum, enc.prevChainLength);
        }
        long decElapsed = System.nanoTime() - decStart;
        double decOps = ITERATIONS * 1_000_000_000.0 / decElapsed;
        System.out.printf("  Decrypt: %.0f ops/sec (%.2f µs/op)%n", decOps, decElapsed / (ITERATIONS * 1000.0));
        System.out.println();
    }

    private static void benchmarkVariousPayloads() throws Exception {
        System.out.println("--- Encrypt Throughput by Payload Size ---");
        Random rng = new Random();

        for (int size : PAYLOAD_SIZES) {
            DoubleRatchetSession alice = new DoubleRatchetSession();
            DoubleRatchetSession bob = new DoubleRatchetSession();
            initSession(alice, bob);

            byte[] payload = new byte[size];
            rng.nextBytes(payload);

            // Warmup
            for (int i = 0; i < 100; i++) alice.encrypt(payload);

            int iters = size > 10000 ? 500 : ITERATIONS;
            long start = System.nanoTime();
            for (int i = 0; i < iters; i++) alice.encrypt(payload);
            long elapsed = System.nanoTime() - start;

            double mbPerSec = (long) size * iters / (elapsed / 1_000_000_000.0) / 1_000_000.0;
            System.out.printf("  %6d bytes: %.2f MB/sec encrypt throughput%n", size, mbPerSec);
        }
        System.out.println();
    }

    private static void benchmarkDHRatchetStep() throws Exception {
        System.out.println("--- DH Ratchet Step (alternating send) ---");
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();
        initSession(alice, bob);

        byte[] payload = "ratchet-step".getBytes();

        // Warmup
        for (int i = 0; i < WARMUP / 10; i++) {
            DoubleRatchetSession.EncryptResult enc = alice.encrypt(payload);
            bob.decrypt(enc.ciphertext, enc.iv, enc.dhPubEncoded, enc.msgNum, enc.prevChainLength);
            DoubleRatchetSession.EncryptResult enc2 = bob.encrypt(payload);
            alice.decrypt(enc2.ciphertext, enc2.iv, enc2.dhPubEncoded, enc2.msgNum, enc2.prevChainLength);
        }

        int iters = ITERATIONS / 10;
        long start = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            DoubleRatchetSession.EncryptResult enc = alice.encrypt(payload);
            bob.decrypt(enc.ciphertext, enc.iv, enc.dhPubEncoded, enc.msgNum, enc.prevChainLength);
            DoubleRatchetSession.EncryptResult enc2 = bob.encrypt(payload);
            alice.decrypt(enc2.ciphertext, enc2.iv, enc2.dhPubEncoded, enc2.msgNum, enc2.prevChainLength);
        }
        long elapsed = System.nanoTime() - start;
        double roundTrips = iters * 1_000_000_000.0 / elapsed;
        System.out.printf("  Alternating send (2x DH ratchet): %.0f round-trips/sec (%.2f ms/round-trip)%n",
                roundTrips, elapsed / (iters * 1_000_000.0));
        System.out.println();
    }

    private static void benchmarkOutOfOrder() throws Exception {
        System.out.println("--- Out-of-Order Decrypt (skip N messages) ---");
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();
        initSession(alice, bob);

        byte[] payload = "ooo-test".getBytes();

        for (int skipN : new int[]{1, 5, 10, 50, 100}) {
            // Generate skipN+1 messages
            DoubleRatchetSession.EncryptResult[] encs = new DoubleRatchetSession.EncryptResult[skipN + 1];
            for (int i = 0; i <= skipN; i++) encs[i] = alice.encrypt(payload);

            // Decrypt last message (forces skipping skipN messages)
            long start = System.nanoTime();
            int iters = ITERATIONS / (skipN + 1);
            for (int r = 0; r < Math.min(iters, 200); r++) {
                DoubleRatchetSession a2 = new DoubleRatchetSession();
                DoubleRatchetSession b2 = new DoubleRatchetSession();
                initSession(a2, b2);
                DoubleRatchetSession.EncryptResult[] e2 = new DoubleRatchetSession.EncryptResult[skipN + 1];
                for (int i = 0; i <= skipN; i++) e2[i] = a2.encrypt(payload);
                b2.decrypt(e2[skipN].ciphertext, e2[skipN].iv, e2[skipN].dhPubEncoded,
                        e2[skipN].msgNum, e2[skipN].prevChainLength);
            }
            long elapsed = System.nanoTime() - start;
            double ms = elapsed / (Math.min(iters, 200) * 1_000_000.0);
            System.out.printf("  Skip %3d messages: %.2f ms/decrypt (with skip)%n", skipN, ms);
        }
        System.out.println();
    }

    private static void initSession(DoubleRatchetSession alice, DoubleRatchetSession bob) throws Exception {
        KeyPair aliceIdKey = DoubleRatchetSession.generateKeyPair();
        KeyPair aliceEphKey = DoubleRatchetSession.generateKeyPair();
        KeyPair bobIdKey = DoubleRatchetSession.generateKeyPair();
        KeyPair bobSignedPrekey = DoubleRatchetSession.generateKeyPair();
        KeyPair bobOtpk = DoubleRatchetSession.generateKeyPair();

        alice.initAsSender(aliceIdKey, aliceEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(),
                bobOtpk.getPublic().getEncoded());

        bob.initAsReceiver(bobIdKey, bobSignedPrekey,
                bobOtpk.getPrivate(),
                aliceIdKey.getPublic(), aliceEphKey.getPublic());
    }
}
