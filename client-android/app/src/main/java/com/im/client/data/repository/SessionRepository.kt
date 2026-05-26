package com.im.client.data.repository

import android.util.Log
import com.im.client.data.local.AppDatabase
import com.im.client.data.local.SessionEntity
import com.im.client.data.model.Session
import com.im.client.data.remote.ApiService
import com.im.client.data.remote.Cmd
import com.im.client.data.remote.MsgType
import com.im.client.data.remote.TcpConnection
import com.im.protocol.ImProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SessionRepository(
    private val apiService: ApiService,
    private val tcpConnection: TcpConnection,
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "SessionRepository"
    }

    private val sessionDao get() = database.sessionDao()

    fun getAllSessionsFlow(): Flow<List<Session>> {
        return sessionDao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getAllSessions(): List<Session> {
        return sessionDao.getAll().map { it.toDomain() }
    }

    suspend fun refreshSessions(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = ImProto.SessionListRequest.newBuilder()
                .setLastUpdateTime(0)
                .setLimit(100)
                .build()

            val response = tcpConnection.sendRequest(Cmd.SESSION_LIST, MsgType.REQUEST, request)
                ?: return@withContext Result.failure(Exception("Session list request timeout"))

            val ack = ImProto.SessionListResponse.parseFrom(response.bodyBytes)
            val entities = ack.sessionsList.map { s ->
                SessionEntity(
                    sessionId = s.sessionId,
                    type = s.type,
                    targetId = s.targetId,
                    name = s.name,
                    avatarUrl = s.avatarUrl,
                    lastMsg = s.lastMsg,
                    lastMsgTime = s.lastMsgTime,
                    unreadCount = s.unreadCount,
                    isTop = s.isTop,
                    isMuted = s.isMuted
                )
            }
            sessionDao.insertAll(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Refresh sessions failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun SessionEntity.toDomain() = Session(
        sessionId = sessionId, type = type, targetId = targetId,
        name = name, avatarUrl = avatarUrl, lastMsg = lastMsg,
        lastMsgTime = lastMsgTime, unreadCount = unreadCount,
        isTop = isTop, isMuted = isMuted
    )
}
