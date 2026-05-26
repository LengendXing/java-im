package com.im.server.logic;

import org.junit.jupiter.api.Test;

import com.im.server.common.Cmd;

import static org.junit.jupiter.api.Assertions.*;

class PushEnvelopeTest {

    @Test
    void encodeDecode_roundTrip() {
        byte[] payload = "notify payload".getBytes();
        byte[] encoded = PushEnvelope.create(42L, 0x0103, payload);

        PushEnvelope decoded = PushEnvelope.decode(encoded);
        assertEquals(42L, decoded.userId);
        assertEquals(0x0103, decoded.cmd);
        assertArrayEquals(payload, decoded.payload);
    }

    @Test
    void encodeDecode_emptyPayload() {
        byte[] encoded = PushEnvelope.create(1L, Cmd.C2C_MSG_NOTIFY, new byte[0]);

        PushEnvelope decoded = PushEnvelope.decode(encoded);
        assertEquals(1L, decoded.userId);
        assertEquals(Cmd.C2C_MSG_NOTIFY, decoded.cmd);
        assertEquals(0, decoded.payload.length);
    }

    @Test
    void create_producesNonNullEncoded() {
        byte[] encoded = PushEnvelope.create(999L, Cmd.GROUP_MSG_NOTIFY, "test".getBytes());
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
    }
}
