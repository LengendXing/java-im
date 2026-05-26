package com.im.client.data.model

data class User(
    val userId: Long,
    val username: String,
    val nickname: String,
    val avatarUrl: String
)

data class Message(
    val msgId: Long,
    val sessionId: String,
    val senderId: Long,
    val seq: Long,
    val msgType: Int,
    val content: String,
    val serverTime: Long,
    val clientMsgId: String = "",
    val isRead: Boolean = false,
    val isMine: Boolean = false
)

data class Session(
    val sessionId: String,
    val type: Int,
    val targetId: Long,
    val name: String,
    val avatarUrl: String,
    val lastMsg: String,
    val lastMsgTime: Long,
    val unreadCount: Int,
    val isTop: Boolean = false,
    val isMuted: Boolean = false
)

data class Group(
    val groupId: Long,
    val name: String,
    val avatarUrl: String,
    val memberCount: Int
)

enum class SessionType(val value: Int) {
    C2C(1), GROUP(2)
}

enum class ContentType(val value: Int) {
    TEXT(1), IMAGE(2), FILE(3), VOICE(4), SYSTEM(5);

    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: TEXT
    }
}
