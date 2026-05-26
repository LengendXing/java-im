package com.im.server.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class RequestEnvelope {
    public long userId;
    public int cmd;
    public byte[] body;

    public RequestEnvelope(long userId, int cmd, byte[] body) {
        this.userId = userId;
        this.cmd = cmd;
        this.body = body;
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeLong(userId);
            dos.writeShort(cmd);
            dos.writeInt(body.length);
            dos.write(body);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static RequestEnvelope decode(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bais);
            long userId = dis.readLong();
            int cmd = dis.readShort() & 0xFFFF;
            int len = dis.readInt();
            byte[] body = new byte[len];
            dis.readFully(body);
            return new RequestEnvelope(userId, cmd, body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
