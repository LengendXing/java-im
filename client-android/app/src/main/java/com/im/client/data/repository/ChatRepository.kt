package com.im.client.data.repository

import android.util.Log
import com.im.client.data.local.AppDatabase
import com.im.client.data.local.MessageEntity
import com.im.client.data.model.Message
import com.im.client.data.remote.Cmd
import com.im.client.data.remote.MsgType
import com.im.client.data.remote.TcpConnection
import com.im.protocol.ImProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(
    private val tcpConnection: TcpConnection,
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "ChatRepository"
    }

    private val messageDao get() = database.messageDao()
    private val sessionDao get() = database.sessionDao()

    fun getMessagesFlow(sessionId: String): Flow<List<Message>> {
        return messageDao.getBySessionFlow(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMessages(sessionId: String): List<Message> {
        return messageDao.getRecentBySession(sessionId).map { it.toDomain() }
    }

    suspend fun sendMessage(receiverId: Long, content: String, msgType: Int = 1): Result<Message> {
        return withContext(Dispatchers.IO) {
            try {
                val clientMsgId = UUID.randomUUID().toString()
                val msgContent = ImProto.MessageContent.newBuilder()
                    .setMsgType(msgType)
                    .setText(content)
                    .build()

                val request = ImProto.C2CMsgRequest.newBuilder()
                    .setReceiverId(receiverId)
                    .setContent(msgContent)
                    .setClientMsgId(clientMsgId)
                    .build()

                val response = tcpConnection.sendRequest(Cmd.C2C_MSG, MsgType.REQUEST, request)
                    ?: return@withContext Result.failure(Exception("Send timeout"))

                val ack = ImProto.C2CMsgResponse.parseFrom(response.bodyBytes)
                if (ack.code != 0) {
                    return@withContext Result.failure(Exception(ack.msg))
                }

                val message = Message(
                    msgId = ack.msgId,
                    sessionId = "c2c_${receiverId}",
                    senderId = 0, // self, will be filled from auth
                    seq = ack.seq,
                    msgType = msgType,
                    content = content,
                    serverTime = ack.serverTime,
                    clientMsgId = clientMsgId,
                    isMine = true,
                    isRead = true
                )
                messageDao.insert(message.toEntity())

                // Update session last message
                sessionDao.updateLastMessage("c2c_${receiverId}", content, ack.serverTime)

                Result.success(message)
            } catch (e: Exception) {
                Log.e(TAG, "Send message failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun sendGroupMessage(groupId: Long, content: String, msgType: Int = 1): Result<Message> {
        return withContext(Dispatchers.IO) {
            try {
                val clientMsgId = UUID.randomUUID().toString()
                val msgContent = ImProto.MessageContent.newBuilder()
                    .setMsgType(msgType)
                    .setText(content)
                    .build()

                val request = ImProto.GroupMsgRequest.newBuilder()
                    .setGroupId(groupId)
                    .setContent(msgContent)
                    .setClientMsgId(clientMsgId)
                    .build()

                val response = tcpConnection.sendRequest(Cmd.GROUP_MSG, MsgType.REQUEST, request)
                    ?: return@withContext Result.failure(Exception("Send timeout"))

                val ack = ImProto.GroupMsgResponse.parseFrom(response.bodyBytes)
                if (ack.code != 0) {
                    return@withContext Result.failure(Exception(ack.msg))
                }

                val message = Message(
                    msgId = ack.msgId,
                    sessionId = "group_${groupId}",
                    senderId = 0,
                    seq = ack.seq,
                    msgType = msgType,
                    content = content,
                    serverTime = ack.serverTime,
                    clientMsgId = clientMsgId,
                    isMine = true,
                    isRead = true
                )
                messageDao.insert(message.toEntity())
                sessionDao.updateLastMessage("group_${groupId}", content, ack.serverTime)

                Result.success(message)
            } catch (e: Exception) {
                Log.e(TAG, "Send group message failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun handleC2CNotify(packet: com.im.client.data.remote.ImPacket) {
        withContext(Dispatchers.IO) {
            try {
                val notify = ImProto.C2CMsgNotify.parseFrom(packet.bodyBytes)
                val entity = MessageEntity(
                    msgId = notify.msgId,
                    sessionId = notify.sessionId,
                    senderId = notify.senderId,
                    seq = notify.seq,
                    msgType = notify.content.msgType,
                    content = notify.content.text,
                    serverTime = notify.serverTime,
                    isRead = false,
                    isMine = false
                )
                messageDao.insert(entity)
                sessionDao.updateLastMessage(notify.sessionId, notify.content.text, notify.serverTime)

                // Send ACK
                val ackReq = ImProto.MsgAckRequest.newBuilder()
                    .setMsgId(notify.msgId)
                    .setSessionId(notify.sessionId)
                    .setSeq(notify.seq)
                    .build()
                tcpConnection.sendNotify(Cmd.MSG_ACK, MsgType.REQUEST, ackReq)
            } catch (e: Exception) {
                Log.e(TAG, "Handle C2C notify failed: ${e.message}")
            }
        }
    }

    suspend fun handleGroupNotify(packet: com.im.client.data.remote.ImPacket) {
        withContext(Dispatchers.IO) {
            try {
                val notify = ImProto.GroupMsgNotify.parseFrom(packet.bodyBytes)
                val entity = MessageEntity(
                    msgId = notify.msgId,
                    sessionId = notify.sessionId,
                    senderId = notify.senderId,
                    seq = notify.seq,
                    msgType = notify.content.msgType,
                    content = notify.content.text,
                    serverTime = notify.serverTime,
                    isRead = false,
                    isMine = false
                )
                messageDao.insert(entity)
                sessionDao.updateLastMessage(notify.sessionId, notify.content.text, notify.serverTime)

                val ackReq = ImProto.MsgAckRequest.newBuilder()
                    .setMsgId(notify.msgId)
                    .setSessionId(notify.sessionId)
                    .setSeq(notify.seq)
                    .build()
                tcpConnection.sendNotify(Cmd.MSG_ACK, MsgType.REQUEST, ackReq)
            } catch (e: Exception) {
                Log.e(TAG, "Handle group notify failed: ${e.message}")
            }
        }
    }

    suspend fun markRead(sessionId: String) {
        messageDao.markSessionRead(sessionId)
    }

    private fun MessageEntity.toDomain() = Message(
        msgId = msgId, sessionId = sessionId, senderId = senderId, seq = seq,
        msgType = msgType, content = content, serverTime = serverTime,
        clientMsgId = clientMsgId, isRead = isRead, isMine = isMine
    )

    private fun Message.toEntity() = MessageEntity(
        msgId = msgId, sessionId = sessionId, senderId = senderId, seq = seq,
        msgType = msgType, content = content, serverTime = serverTime,
        clientMsgId = clientMsgId, isRead = isRead, isMine = isMine
    )

    // Room doesn't return Flow from DAO directly with suspend, use this helper
    private fun messageDao.getBySessionFlow(sessionId: String): Flow<List<MessageEntity>> {
        return database.messageDao().getBySessionFlow(sessionId)
    }
}
