package com.im.client.data.remote

object Cmd {
    const val MAGIC: Int = 0x4D49
    const val VERSION: Int = 0x0001
    const val HEADER_LEN: Int = 22

    const val AUTH: Int = 0x0001
    const val AUTH_ACK: Int = 0x0002
    const val HEARTBEAT: Int = 0x0003
    const val HEARTBEAT_ACK: Int = 0x0004
    const val C2C_MSG: Int = 0x0101
    const val C2C_MSG_ACK: Int = 0x0102
    const val C2C_MSG_NOTIFY: Int = 0x0103
    const val GROUP_MSG: Int = 0x0201
    const val GROUP_MSG_ACK: Int = 0x0202
    const val GROUP_MSG_NOTIFY: Int = 0x0203
    const val MSG_ACK: Int = 0x0110
    const val SYNC: Int = 0x0301
    const val SYNC_ACK: Int = 0x0302
    const val SESSION_LIST: Int = 0x0401
    const val SESSION_LIST_ACK: Int = 0x0402
    const val FRIEND_APPLY: Int = 0x0501
    const val FRIEND_LIST: Int = 0x0502
    const val FRIEND_REQUEST_LIST: Int = 0x0504
    const val FRIEND_ACCEPT: Int = 0x0511
    const val FRIEND_REJECT: Int = 0x0512
    const val GROUP_CREATE: Int = 0x0601
    const val GROUP_MEMBER_LIST: Int = 0x0602
    const val GROUP_INVITE: Int = 0x0603
    const val GROUP_KICK: Int = 0x0604
    const val GROUP_DISSOLVE: Int = 0x0605
    const val MSG_ACK: Int = 0x0110
    const val MSG_READ: Int = 0x0111
    const val MSG_RECALL: Int = 0x0121
    const val FILE_UPLOAD: Int = 0x0701
    const val KICKOFF: Int = 0x0F01
}

object MsgType {
    const val REQUEST: Int = 0
    const val RESPONSE: Int = 1
    const val NOTIFY: Int = 2
}
