package com.im.server.common;

public final class Cmd {
    private Cmd() {}
    public static final int MAGIC = 0x4D49;
    public static final int VERSION = 0x0001;
    public static final int HEADER_LEN = 22;

    public static final int AUTH = 0x0001;
    public static final int AUTH_ACK = 0x0002;
    public static final int HEARTBEAT = 0x0003;
    public static final int HEARTBEAT_ACK = 0x0004;
    public static final int C2C_MSG = 0x0101;
    public static final int C2C_MSG_ACK = 0x0102;
    public static final int C2C_MSG_NOTIFY = 0x0103;
    public static final int GROUP_MSG = 0x0201;
    public static final int GROUP_MSG_ACK = 0x0202;
    public static final int GROUP_MSG_NOTIFY = 0x0203;
    public static final int MSG_ACK = 0x0110;
    public static final int SYNC = 0x0301;
    public static final int SYNC_ACK = 0x0302;
    public static final int SESSION_LIST = 0x0401;
    public static final int SESSION_LIST_ACK = 0x0402;
    public static final int FRIEND_APPLY = 0x0501;
    public static final int FRIEND_LIST = 0x0502;
    public static final int GROUP_CREATE = 0x0601;
    public static final int GROUP_MEMBER_LIST = 0x0602;
    public static final int KICKOFF = 0x0F01;
}
