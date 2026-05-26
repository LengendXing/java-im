package com.im.client.data.repository

import android.util.Log
import com.im.client.data.model.Group
import com.im.client.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GroupRepository(
    private val apiService: ApiService
) {
    companion object {
        private const val TAG = "GroupRepository"
    }

    suspend fun createGroup(token: String, name: String, memberIds: List<Long>): Result<Group> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = apiService.createGroup(token, name, memberIds)
                Result.success(Group(
                    groupId = resp.groupId,
                    name = resp.name,
                    avatarUrl = resp.avatarUrl,
                    memberCount = resp.memberCount
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Create group failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun inviteToGroup(token: String, groupId: Long, userId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.inviteToGroup(token, groupId, userId)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Invite to group failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun kickFromGroup(token: String, groupId: Long, userId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.kickFromGroup(token, groupId, userId)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Kick from group failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun dissolveGroup(token: String, groupId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.dissolveGroup(token, groupId)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Dissolve group failed: ${e.message}")
                Result.failure(e)
            }
        }
    }
}
