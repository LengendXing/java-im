package com.im.server.common;

import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketCodec {
    private static final Logger log = LoggerFactory.getLogger(PacketCodec.class);

    public static ImPacket decode(Buffer buf) {
        if (buf.length() < Cmd.HEADER_LEN) return null;

        int offset = 0;
        int magic = buf.getShort(offset) & 0xFFFF; offset += 2;
        if (magic != Cmd.MAGIC) {
            log.warn("invalid magic: 0x{}", Integer.toHexString(magic));
            return null;
        }

        offset += 2; // version
        int cmd = buf.getShort(offset) & 0xFFFF; offset += 2;
        int msgType = buf.getByte(offset) & 0xFF; offset += 1;
        int sequenceId = buf.getInt(offset); offset += 4;
        int dataLength = buf.getInt(offset); offset += 4;
        offset += 7; // padding

        if (buf.length() < Cmd.HEADER_LEN + dataLength) return null;

        byte[] bodyBytes = buf.getBytes(offset, offset + dataLength);
        return new ImPacket(cmd, msgType, sequenceId, bodyBytes);
    }

    public static Buffer encode(ImPacket packet) {
        return packet.encode();
    }
}
