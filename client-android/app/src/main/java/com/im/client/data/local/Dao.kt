package com.im.client.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "message", indices = [Index(value = ["session_id", "seq"], unique = true)])
data class MessageEntity(
    @PrimaryKey @ColumnInfo(name = "msg_id") val msgId: Long,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "sender_id") val senderId: Long,
    val seq: Long,
    @ColumnInfo(name = "msg_type") val msgType: Int,
    val content: String,
    @ColumnInfo(name = "server_time") val serverTime: Long,
    @ColumnInfo(name = "client_msg_id") val clientMsgId: String = "",
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "is_mine") val isMine: Boolean = false
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM message WHERE session_id = :sessionId ORDER BY seq ASC")
    fun getBySessionFlow(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE session_id = :sessionId ORDER BY seq ASC")
    suspend fun getBySession(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM message WHERE session_id = :sessionId ORDER BY seq DESC LIMIT :limit")
    suspend fun getRecentBySession(sessionId: String, limit: Int = 50): List<MessageEntity>

    @Query("SELECT * FROM message WHERE session_id = :sessionId AND seq > :afterSeq ORDER BY seq ASC")
    suspend fun getBySessionAfterSeq(sessionId: String, afterSeq: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE message SET is_read = 1 WHERE session_id = :sessionId")
    suspend fun markSessionRead(sessionId: String)

    @Query("SELECT COUNT(*) FROM message WHERE session_id = :sessionId AND is_read = 0 AND is_mine = 0")
    suspend fun getUnreadCount(sessionId: String): Int
}

@Entity(tableName = "session", indices = [Index(value = ["session_id"], unique = true)])
data class SessionEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "target_id") val targetId: Long,
    val name: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String,
    @ColumnInfo(name = "last_msg") val lastMsg: String,
    @ColumnInfo(name = "last_msg_time") val lastMsgTime: Long,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
    @ColumnInfo(name = "is_top") val isTop: Boolean = false,
    @ColumnInfo(name = "is_muted") val isMuted: Boolean = false
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM session ORDER BY is_top DESC, last_msg_time DESC")
    fun getAllFlow(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session ORDER BY is_top DESC, last_msg_time DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT * FROM session WHERE session_id = :sessionId")
    suspend fun getById(sessionId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("UPDATE session SET unread_count = :count WHERE session_id = :sessionId")
    suspend fun updateUnreadCount(sessionId: String, count: Int)

    @Query("UPDATE session SET last_msg = :msg, last_msg_time = :time WHERE session_id = :sessionId")
    suspend fun updateLastMessage(sessionId: String, msg: String, time: Long)

    @Query("DELETE FROM session WHERE session_id = :sessionId")
    suspend fun delete(sessionId: String)
}
