package com.im.server.common;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PacketCodecTest {

    @Test
    void encodeAndDecode_roundTrip() {
        byte[] body = "hello world".getBytes();
        ImPacket original = new ImPacket(Cmd.C2C_MSG, MsgType.REQUEST, 42, body);
        Buffer encoded = original.encode();

        assertEquals(Cmd.HEADER_LEN + body.length, encoded.length());

        ImPacket decoded = PacketCodec.decode(encoded);
        assertNotNull(decoded);
        assertEquals(Cmd.C2C_MSG, decoded.getCmd());
        assertEquals(MsgType.REQUEST, decoded.getMsgType());
        assertEquals(42, decoded.getSequenceId());
        assertArrayEquals(body, decoded.getBodyBytes());
    }

    @Test
    void decode_invalidMagic_returnsNull() {
        Buffer buf = Buffer.buffer(Cmd.HEADER_LEN + 4);
        buf.setShort(0, (short) 0x1234); // wrong magic
        buf.setShort(2, (short) 1);
        buf.setShort(4, (short) Cmd.AUTH);
        buf.setByte(6, (byte) MsgType.REQUEST);
        buf.setInt(7, 1);
        buf.setInt(11, 4);
        buf.setInt(15, 0); // padding start
        buf.setBytes(22, "test".getBytes());

        ImPacket decoded = PacketCodec.decode(buf);
        assertNull(decoded);
    }

    @Test
    void decode_bufferTooShort_returnsNull() {
        Buffer shortBuf = Buffer.buffer(10);
        ImPacket decoded = PacketCodec.decode(shortBuf);
        assertNull(decoded);
    }

    @Test
    void encode_allCmdTypes() {
        int[] cmds = {Cmd.AUTH, Cmd.HEARTBEAT, Cmd.C2C_MSG, Cmd.GROUP_MSG, Cmd.SESSION_LIST, Cmd.SYNC};
        for (int cmd : cmds) {
            ImPacket packet = new ImPacket(cmd, MsgType.REQUEST, 1, new byte[0]);
            Buffer encoded = packet.encode();
            assertEquals(Cmd.HEADER_LEN, encoded.length());

            ImPacket decoded = PacketCodec.decode(encoded);
            assertNotNull(decoded);
            assertEquals(cmd, decoded.getCmd());
        }
    }

    @Test
    void headerStructure_offsets() {
        byte[] body = new byte[]{1, 2, 3, 4, 5};
        ImPacket packet = new ImPacket(Cmd.C2C_MSG_NOTIFY, MsgType.NOTIFY, 9999, body);
        Buffer buf = packet.encode();

        // Verify header field offsets
        assertEquals(Cmd.MAGIC, buf.getShort(0) & 0xFFFF);
        assertEquals(Cmd.VERSION, buf.getShort(2) & 0xFFFF);
        assertEquals(Cmd.C2C_MSG_NOTIFY, buf.getShort(4) & 0xFFFF);
        assertEquals(MsgType.NOTIFY, buf.getByte(6) & 0xFF);
        assertEquals(9999, buf.getInt(7));
        assertEquals(5, buf.getInt(11));
    }
}
