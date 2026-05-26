package com.im.client.network;

public final class ImPacket {

    public static final int HEADER_SIZE = 22;
    public static final short MAGIC = 0x4D49;
    public static final short VERSION = 1;

    public static final short CMD_AUTH = 1;
    public static final short CMD_HEARTBEAT = 2;
    public static final short CMD_C2C_MSG = 3;
    public static final short CMD_GROUP_MSG = 4;
    public static final short CMD_C2C_MSG_NOTIFY = 5;
    public static final short CMD_GROUP_MSG_NOTIFY = 6;
    public static final short CMD_MSG_ACK = 7;
    public static final short CMD_SYNC = 8;
    public static final short CMD_SESSION_LIST = 9;
    public static final short CMD_FRIEND_APPLY = 10;
    public static final short CMD_FRIEND_LIST = 11;
    public static final short CMD_GROUP_CREATE = 12;
    public static final short CMD_GROUP_MEMBER_LIST = 13;

    public static final byte MSG_TYPE_REQUEST = 1;
    public static final byte MSG_TYPE_RESPONSE = 2;
    public static final byte MSG_TYPE_NOTIFY = 3;

    private short version = VERSION;
    private short cmd;
    private byte msgType;
    private int sequenceId;
    private byte[] body;

    public ImPacket() {
    }

    public ImPacket(short cmd, byte msgType, int sequenceId, byte[] body) {
        this.cmd = cmd;
        this.msgType = msgType;
        this.sequenceId = sequenceId;
        this.body = body != null ? body : new byte[0];
    }

    public short getVersion() { return version; }
    public void setVersion(short version) { this.version = version; }

    public short getCmd() { return cmd; }
    public void setCmd(short cmd) { this.cmd = cmd; }

    public byte getMsgType() { return msgType; }
    public void setMsgType(byte msgType) { this.msgType = msgType; }

    public int getSequenceId() { return sequenceId; }
    public void setSequenceId(int sequenceId) { this.sequenceId = sequenceId; }

    public byte[] getBody() { return body; }
    public void setBody(byte[] body) { this.body = body; }

    public int getDataLength() {
        return body != null ? body.length : 0;
    }

    @Override
    public String toString() {
        return "ImPacket{cmd=" + cmd + ", msgType=" + msgType +
                ", seqId=" + sequenceId + ", bodyLen=" + getDataLength() + "}";
    }
}
