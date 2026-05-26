package com.im.server.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthVerticleTest {

    @Test
    void hashPassword_deterministic() {
        String salt = AuthVerticle.generateSalt();
        String hash1 = AuthVerticle.hashPassword("password123", salt);
        String hash2 = AuthVerticle.hashPassword("password123", salt);
        assertEquals(hash1, hash2);
    }

    @Test
    void hashPassword_differentSaltsProduceDifferentHashes() {
        String salt1 = AuthVerticle.generateSalt();
        String salt2 = AuthVerticle.generateSalt();
        String hash1 = AuthVerticle.hashPassword("password123", salt1);
        String hash2 = AuthVerticle.hashPassword("password123", salt2);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashPassword_differentPasswordsProduceDifferentHashes() {
        String salt = AuthVerticle.generateSalt();
        String hash1 = AuthVerticle.hashPassword("password1", salt);
        String hash2 = AuthVerticle.hashPassword("password2", salt);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void generateSalt_uniqueAndNonEmpty() {
        String salt1 = AuthVerticle.generateSalt();
        String salt2 = AuthVerticle.generateSalt();
        assertNotNull(salt1);
        assertFalse(salt1.isEmpty());
        assertNotEquals(salt1, salt2);
    }

    @Test
    void hashPassword_emptyPassword() {
        String salt = AuthVerticle.generateSalt();
        String hash = AuthVerticle.hashPassword("", salt);
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
    }
}
