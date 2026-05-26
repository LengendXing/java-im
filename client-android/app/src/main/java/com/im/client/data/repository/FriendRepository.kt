package com.im.client.data.repository

import android.util.Log
import com.im.client.data.model.FriendRequest
import com.im.client.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FriendRepository(
    private val apiService: ApiService
) {
    companion object {
        private const val TAG = "FriendRepository"
    }

    suspend fun getFriendRequests(token: String): List<FriendRequest> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.getFriendRequests(token).map { it.toDomain() }
            } catch (e: Exception) {
                Log.e(TAG, "Get friend requests failed: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun acceptFriend(token: String, fromUserId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.acceptFriend(token, fromUserId)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Accept friend failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun rejectFriend(token: String, fromUserId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.rejectFriend(token, fromUserId)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Reject friend failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun getFriendList(token: String): List<ApiService.FriendListResponse> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.getFriendList(token)
            } catch (e: Exception) {
                Log.e(TAG, "Get friend list failed: ${e.message}")
                emptyList()
            }
        }
    }

    private fun ApiService.FriendRequestItem.toDomain() = FriendRequest(
        requestId = requestId,
        fromUserId = fromUserId,
        fromNickname = fromNickname,
        fromAvatarUrl = fromAvatarUrl,
        message = message,
        createTime = createTime,
        status = status
    )
}
