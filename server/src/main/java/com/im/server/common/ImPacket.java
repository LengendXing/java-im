package com.im.server.common;

import com.google.protobuf.GeneratedMessageV3;
import io.vertx.core.buffer.Buffer;

import java.nio.ByteBuffer;

public class ImPacket {
    private final int cmd;
    private final int msgType;
    private final int sequenceId;
    private final byte[] bodyBytes;

    public ImPacket(int cmd, int msgType, int sequenceId, byte[] bodyBytes) {
        this.cmd = cmd;
        this.msgType = msgType;
        this.sequenceId = sequenceId;
        this.bodyBytes = bodyBytes;
    }

    public ImPacket(int cmd, int msgType, int sequenceId, GeneratedMessageV3 body) {
        this(cmd, msgType, sequenceId, body.toByteArray());
    }

    public int getCmd() { return cmd; }
    public int getMsgType() { return msgType; }
    public int getSequenceId() { return sequenceId; }
    public byte[] getBodyBytes() { return bodyBytes; }

    public Buffer encode() {
        int totalLen = Cmd.HEADER_LEN + bodyBytes.length;
        ByteBuffer buf = ByteBuffer.allocate(totalLen);
        buf.putShort((short) Cmd.MAGIC);
        buf.putShort((short) Cmd.VERSION);
        buf.putShort((short) cmd);
        buf.put((byte) msgType);
        buf.putInt(sequenceId);
        buf.putInt(bodyBytes.length);
        buf.put(new byte[7]);
        buf.put(bodyBytes);
        return Buffer.buffer(buf.array());
    }
}
