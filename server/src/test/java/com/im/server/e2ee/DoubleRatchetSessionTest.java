package com.im.server.e2ee;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DoubleRatchetSessionTest {

    private KeyPair aliceIdKey;
    private KeyPair aliceEphKey;
    private KeyPair bobIdKey;
    private KeyPair bobSignedPrekey;
    private KeyPair bobOtpk;

    @BeforeEach
    void setUp() throws Exception {
        aliceIdKey = DoubleRatchetSession.generateKeyPair();
        aliceEphKey = DoubleRatchetSession.generateKeyPair();
        bobIdKey = DoubleRatchetSession.generateKeyPair();
        bobSignedPrekey = DoubleRatchetSession.generateKeyPair();
        bobOtpk = DoubleRatchetSession.generateKeyPair();
    }

    private byte[] dec(DoubleRatchetSession session, DoubleRatchetSession.EncryptResult enc) throws Exception {
        return session.decrypt(enc.ciphertext, enc.iv, enc.dhPubEncoded, enc.msgNum, enc.prevChainLength);
    }

    @Test
    void generateKeyPair_producesValidX25519Pair() throws Exception {
        KeyPair kp = DoubleRatchetSession.generateKeyPair();
        assertNotNull(kp.getPrivate());
        assertNotNull(kp.getPublic());
        assertTrue(kp.getPrivate().getEncoded().length >= 32);
    }

    @Test
    void x3dh_initSenderThenReceiver_roundTripEncrypt() throws Exception {
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();

        alice.initAsSender(aliceIdKey, aliceEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(),
                bobOtpk.getPublic().getEncoded());

        bob.initAsReceiver(bobIdKey, bobSignedPrekey,
                bobOtpk.getPrivate(),
                aliceIdKey.getPublic(), aliceEphKey.getPublic());

        byte[] plaintext = "Hello Bob!".getBytes();
        DoubleRatchetSession.EncryptResult enc = alice.encrypt(plaintext);
        byte[] decrypted = dec(bob, enc);
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void multipleMessagesInSequence() throws Exception {
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();

        alice.initAsSender(aliceIdKey, aliceEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(),
                bobOtpk.getPublic().getEncoded());

        bob.initAsReceiver(bobIdKey, bobSignedPrekey,
                bobOtpk.getPrivate(),
                aliceIdKey.getPublic(), aliceEphKey.getPublic());

        for (int i = 0; i < 10; i++) {
            byte[] msg = ("Message " + i).getBytes();
            DoubleRatchetSession.EncryptResult enc = alice.encrypt(msg);
            assertArrayEquals(msg, dec(bob, enc), "Message " + i + " failed");
        }
    }

    @Test
    void bidirectionalMessaging() throws Exception {
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();

        alice.initAsSender(aliceIdKey, aliceEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(),
                bobOtpk.getPublic().getEncoded());

        bob.initAsReceiver(bobIdKey, bobSignedPrekey,
                bobOtpk.getPrivate(),
                aliceIdKey.getPublic(), aliceEphKey.getPublic());

        byte[] msg1 = "Hello from Alice".getBytes();
        DoubleRatchetSession.EncryptResult enc1 = alice.encrypt(msg1);
        assertArrayEquals(msg1, dec(bob, enc1));

        byte[] msg2 = "Hello from Bob".getBytes();
        DoubleRatchetSession.EncryptResult enc2 = bob.encrypt(msg2);
        assertArrayEquals(msg2, dec(alice, enc2));

        byte[] msg3 = "Alice again".getBytes();
        DoubleRatchetSession.EncryptResult enc3 = alice.encrypt(msg3);
        assertArrayEquals(msg3, dec(bob, enc3));
    }

    @Test
    void outOfOrderMessages() throws Exception {
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();

        alice.initAsSender(aliceIdKey, aliceEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(),
                bobOtpk.getPublic().getEncoded());

        bob.initAsReceiver(bobIdKey, bobSignedPrekey,
                bobOtpk.getPrivate(),
                aliceIdKey.getPublic(), aliceEphKey.getPublic());

        DoubleRatchetSession.EncryptResult[] encs = new DoubleRatchetSession.EncryptResult[5];
        for (int i = 0; i < 5; i++) {
            encs[i] = alice.encrypt(("Msg " + i).getBytes());
        }

        assertArrayEquals("Msg 2".getBytes(), dec(bob, encs[2]));
        assertArrayEquals("Msg 0".getBytes(), dec(bob, encs[0]));
        assertArrayEquals("Msg 4".getBytes(), dec(bob, encs[4]));
        assertArrayEquals("Msg 1".getBytes(), dec(bob, encs[1]));
        assertArrayEquals("Msg 3".getBytes(), dec(bob, encs[3]));
    }

    @Test
    void outOfOrderAcrossDHRatchet() throws Exception {
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();

        alice.initAsSender(aliceIdKey, aliceEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(),
                bobOtpk.getPublic().getEncoded());

        bob.initAsReceiver(bobIdKey, bobSignedPrekey,
                bobOtpk.getPrivate(),
                aliceIdKey.getPublic(), aliceEphKey.getPublic());

        // Alice sends 2 messages (same DH epoch)
        DoubleRatchetSession.EncryptResult encA0 = alice.encrypt("A0".getBytes());
        DoubleRatchetSession.EncryptResult encA1 = alice.encrypt("A1".getBytes());

        // Bob decrypts A0, then replies (triggers DH ratchet on Alice's side)
        dec(bob, encA0);
        DoubleRatchetSession.EncryptResult encB0 = bob.encrypt("B0".getBytes());

        // Alice receives Bob's reply (DH ratchet)
        dec(alice, encB0);

        // Alice sends 2 more messages (new DH epoch)
        DoubleRatchetSession.EncryptResult encA2 = alice.encrypt("A2".getBytes());
        DoubleRatchetSession.EncryptResult encA3 = alice.encrypt("A3".getBytes());

        // Bob receives out of order: A3 first (triggers ratchet, skips A1 in old chain via prevChainLength)
        assertArrayEquals("A3".getBytes(), dec(bob, encA3));

        // Then A1 from old chain (found in skipped keys)
        assertArrayEquals("A1".getBytes(), dec(bob, encA1));

        // Then A2 from new chain (found in skipped keys)
        assertArrayEquals("A2".getBytes(), dec(bob, encA2));
    }

    @Test
    void maxSkipExceeded_throwsSecurityException() throws Exception {
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();

        alice.initAsSender(aliceIdKey, aliceEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(),
                bobOtpk.getPublic().getEncoded());

        bob.initAsReceiver(bobIdKey, bobSignedPrekey,
                bobOtpk.getPrivate(),
                aliceIdKey.getPublic(), aliceEphKey.getPublic());

        DoubleRatchetSession.EncryptResult enc = alice.encrypt("test".getBytes());
        assertThrows(SecurityException.class, () ->
                bob.decrypt(enc.ciphertext, enc.iv, enc.dhPubEncoded, 1001, 0));
    }

    @Test
    void wrongKeyCannotDecrypt() throws Exception {
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();
        DoubleRatchetSession eve = new DoubleRatchetSession();

        alice.initAsSender(aliceIdKey, aliceEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(),
                bobOtpk.getPublic().getEncoded());

        bob.initAsReceiver(bobIdKey, bobSignedPrekey,
                bobOtpk.getPrivate(),
                aliceIdKey.getPublic(), aliceEphKey.getPublic());

        KeyPair eveIdKey = DoubleRatchetSession.generateKeyPair();
        KeyPair eveEphKey = DoubleRatchetSession.generateKeyPair();
        eve.initAsSender(eveIdKey, eveEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(),
                bobOtpk.getPublic().getEncoded());

        byte[] msg = "Secret".getBytes();
        DoubleRatchetSession.EncryptResult enc = alice.encrypt(msg);

        assertThrows(Exception.class, () -> dec(eve, enc));
    }

    @Test
    void x3dhWithoutOtpk() throws Exception {
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();

        alice.initAsSender(aliceIdKey, aliceEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(), null);

        bob.initAsReceiver(bobIdKey, bobSignedPrekey,
                null, aliceIdKey.getPublic(), aliceEphKey.getPublic());

        byte[] msg = "No OTPK".getBytes();
        DoubleRatchetSession.EncryptResult enc = alice.encrypt(msg);
        assertArrayEquals(msg, dec(bob, enc));
    }

    @Test
    void extendedBidirectionalConversation() throws Exception {
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();

        alice.initAsSender(aliceIdKey, aliceEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(),
                bobOtpk.getPublic().getEncoded());

        bob.initAsReceiver(bobIdKey, bobSignedPrekey,
                bobOtpk.getPrivate(),
                aliceIdKey.getPublic(), aliceEphKey.getPublic());

        String[] aliceMsgs = {"Hi", "How are you?", "That's great!", "See you later"};
        String[] bobMsgs = {"Hey", "I'm good, you?", "Thanks!"};

        for (String m : aliceMsgs) {
            DoubleRatchetSession.EncryptResult enc = alice.encrypt(m.getBytes());
            assertArrayEquals(m.getBytes(), dec(bob, enc));
        }

        for (String m : bobMsgs) {
            DoubleRatchetSession.EncryptResult enc = bob.encrypt(m.getBytes());
            assertArrayEquals(m.getBytes(), dec(alice, enc));
        }
    }

    @Test
    void largePayload() throws Exception {
        DoubleRatchetSession alice = new DoubleRatchetSession();
        DoubleRatchetSession bob = new DoubleRatchetSession();

        alice.initAsSender(aliceIdKey, aliceEphKey,
                bobIdKey.getPublic(), bobSignedPrekey.getPublic(),
                bobOtpk.getPublic().getEncoded());

        bob.initAsReceiver(bobIdKey, bobSignedPrekey,
                bobOtpk.getPrivate(),
                aliceIdKey.getPublic(), aliceEphKey.getPublic());

        byte[] largePayload = new byte[65536];
        new Random().nextBytes(largePayload);

        DoubleRatchetSession.EncryptResult enc = alice.encrypt(largePayload);
        assertArrayEquals(largePayload, dec(bob, enc));
    }
}
