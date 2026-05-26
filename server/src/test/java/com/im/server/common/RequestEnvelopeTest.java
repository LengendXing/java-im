package com.im.server.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestEnvelopeTest {

    @Test
    void encodeDecode_roundTrip() {
        byte[] body = "test payload".getBytes();
        RequestEnvelope original = new RequestEnvelope(12345L, Cmd.C2C_MSG, body);
        byte[] encoded = original.encode();

        RequestEnvelope decoded = RequestEnvelope.decode(encoded);
        assertEquals(12345L, decoded.userId);
        assertEquals(Cmd.C2C_MSG, decoded.cmd);
        assertArrayEquals(body, decoded.body);
    }

    @Test
    void encodeDecode_emptyBody() {
        RequestEnvelope original = new RequestEnvelope(1L, Cmd.AUTH, new byte[0]);
        byte[] encoded = original.encode();

        RequestEnvelope decoded = RequestEnvelope.decode(encoded);
        assertEquals(1L, decoded.userId);
        assertEquals(Cmd.AUTH, decoded.cmd);
        assertEquals(0, decoded.body.length);
    }

    @Test
    void encodeDecode_largeBody() {
        byte[] body = new byte[4096];
        for (int i = 0; i < body.length; i++) body[i] = (byte) (i % 256);
        RequestEnvelope original = new RequestEnvelope(Long.MAX_VALUE, Cmd.SYNC, body);
        byte[] encoded = original.encode();

        RequestEnvelope decoded = RequestEnvelope.decode(encoded);
        assertEquals(Long.MAX_VALUE, decoded.userId);
        assertEquals(Cmd.SYNC, decoded.cmd);
        assertArrayEquals(body, decoded.body);
    }
}
