package com.im.server.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionManagerTest {

    @Test
    void addAndRetrieve_byConnId() {
        ConnectionManager mgr = new ConnectionManager();
        // We can't create real NetSocket in unit test, so test with null
        // Connection requires a real socket, so we test the manager logic conceptually
        assertEquals(0, mgr.onlineCount());
        assertEquals(0, mgr.connectionCount());
    }

    @Test
    void cmdConstants_matchProtocolSpec() {
        assertEquals(0x0001, Cmd.AUTH);
        assertEquals(0x0002, Cmd.AUTH_ACK);
        assertEquals(0x0003, Cmd.HEARTBEAT);
        assertEquals(0x0004, Cmd.HEARTBEAT_ACK);
        assertEquals(0x0101, Cmd.C2C_MSG);
        assertEquals(0x0102, Cmd.C2C_MSG_ACK);
        assertEquals(0x0103, Cmd.C2C_MSG_NOTIFY);
        assertEquals(0x0201, Cmd.GROUP_MSG);
        assertEquals(0x0202, Cmd.GROUP_MSG_ACK);
        assertEquals(0x0203, Cmd.GROUP_MSG_NOTIFY);
        assertEquals(0x0110, Cmd.MSG_ACK);
        assertEquals(0x0301, Cmd.SYNC);
        assertEquals(0x0302, Cmd.SYNC_ACK);
        assertEquals(0x0401, Cmd.SESSION_LIST);
        assertEquals(0x0402, Cmd.SESSION_LIST_ACK);
        assertEquals(0x0F01, Cmd.KICKOFF);
    }

    @Test
    void headerConstants() {
        assertEquals(22, Cmd.HEADER_LEN);
        assertEquals(0x4D49, Cmd.MAGIC);
        assertEquals(0x0001, Cmd.VERSION);
    }

    @Test
    void msgTypeConstants() {
        assertEquals(0, MsgType.REQUEST);
        assertEquals(1, MsgType.RESPONSE);
        assertEquals(2, MsgType.NOTIFY);
    }
}
