package com.im.client.network;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class PacketCodec {

    private PacketCodec() {
    }

    public static byte[] encode(ImPacket packet) {
        int bodyLen = packet.getDataLength();
        ByteBuffer buffer = ByteBuffer.allocate(ImPacket.HEADER_SIZE + bodyLen);
        buffer.putShort(ImPacket.MAGIC);
        buffer.putShort(packet.getVersion());
        buffer.putShort(packet.getCmd());
        buffer.put(packet.getMsgType());
        buffer.putInt(packet.getSequenceId());
        buffer.putInt(bodyLen);
        buffer.put(new byte[7]); // padding
        if (packet.getBody() != null && packet.getBody().length > 0) {
            buffer.put(packet.getBody());
        }
        return buffer.array();
    }

    public static ImPacket decode(DataInputStream in) throws IOException {
        short magic = in.readShort();
        if (magic != ImPacket.MAGIC) {
            throw new IOException("Invalid magic: 0x" + Integer.toHexString(magic));
        }
        ImPacket packet = new ImPacket();
        packet.setVersion(in.readShort());
        packet.setCmd(in.readShort());
        packet.setMsgType(in.readByte());
        packet.setSequenceId(in.readInt());
        int dataLength = in.readInt();
        in.readFully(new byte[7]); // padding
        if (dataLength > 0) {
            byte[] body = new byte[dataLength];
            in.readFully(body);
            packet.setBody(body);
        } else {
            packet.setBody(new byte[0]);
        }
        return packet;
    }
}
