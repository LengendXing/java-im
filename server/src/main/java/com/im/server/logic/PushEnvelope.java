package com.im.server.logic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class PushEnvelope {
    public long userId;
    public int cmd;
    public byte[] payload;

    public PushEnvelope(long userId, int cmd, byte[] payload) {
        this.userId = userId;
        this.cmd = cmd;
        this.payload = payload;
    }

    public static byte[] create(long userId, int cmd, byte[] payload) {
        PushEnvelope e = new PushEnvelope(userId, cmd, payload);
        return e.encode();
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeLong(userId);
            dos.writeShort(cmd);
            dos.writeInt(payload.length);
            dos.write(payload);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static PushEnvelope decode(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bais);
            long userId = dis.readLong();
            int cmd = dis.readShort() & 0xFFFF;
            int len = dis.readInt();
            byte[] payload = new byte[len];
            dis.readFully(payload);
            return new PushEnvelope(userId, cmd, payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
