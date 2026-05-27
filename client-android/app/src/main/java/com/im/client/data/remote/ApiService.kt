package com.im.client.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiService private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS) // longer for file uploads
        .build()

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private var baseUrl: String = "http://10.0.2.2:8080"

    companion object {
        private const val TAG = "ApiService"
        @Volatile
        private var instance: ApiService? = null

        fun getInstance(): ApiService {
            return instance ?: synchronized(this) {
                instance ?: ApiService().also { instance = it }
            }
        }
    }

    fun configure(host: String, httpPort: Int) {
        baseUrl = "http://$host:$httpPort"
    }

    // --- Request/Response data classes ---

    data class RegisterRequest(
        val username: String,
        val password: String,
        val nickname: String
    )

    data class LoginRequest(
        val username: String,
        val password: String
    )

    data class AuthResponse(
        val code: Int,
        val msg: String,
        @SerializedName("user_id") val userId: Long,
        val username: String,
        val nickname: String,
        val token: String,
        @SerializedName("avatar_url") val avatarUrl: String? = null
    )

    data class ApiResponse<T>(
        val code: Int,
        val msg: String,
        val data: T?
    )

    data class FileUploadResponse(
        val url: String,
        val fileName: String,
        val fileSize: Long
    )

    data class FriendRequestItem(
        @SerializedName("request_id") val requestId: Long,
        @SerializedName("from_user_id") val fromUserId: Long,
        @SerializedName("from_nickname") val fromNickname: String,
        @SerializedName("from_avatar_url") val fromAvatarUrl: String,
        val message: String,
        @SerializedName("create_time") val createTime: Long,
        val status: Int
    )

    data class FriendActionRequest(
        @SerializedName("from_user_id") val fromUserId: Long
    )

    data class FriendApplyRequest(
        @SerializedName("target_user_id") val targetUserId: Long,
        val message: String = ""
    )

    data class GroupCreateRequest(
        val name: String,
        @SerializedName("member_ids") val memberIds: List<Long>
    )

    data class GroupInviteRequest(
        @SerializedName("group_id") val groupId: Long,
        @SerializedName("user_ids") val userIds: List<Long>
    )

    data class GroupKickRequest(
        @SerializedName("group_id") val groupId: Long,
        @SerializedName("user_id") val userId: Long
    )

    data class GroupDissolveRequest(
        @SerializedName("group_id") val groupId: Long
    )

    data class GroupInfoResponse(
        @SerializedName("group_id") val groupId: Long,
        val name: String,
        @SerializedName("avatar_url") val avatarUrl: String,
        @SerializedName("member_count") val memberCount: Int,
        @SerializedName("owner_id") val ownerId: Long
    )

    data class MessageRecallRequest(
        @SerializedName("msg_id") val msgId: Long,
        @SerializedName("session_id") val sessionId: String
    )

    data class MessageReadRequest(
        @SerializedName("session_id") val sessionId: String,
        @SerializedName("last_read_seq") val lastReadSeq: Long
    )

    data class MessageSearchResult(
        @SerializedName("msg_id") val msgId: Long,
        @SerializedName("session_id") val sessionId: String,
        @SerializedName("sender_id") val senderId: Long,
        val seq: Long,
        @SerializedName("msg_type") val msgType: Int,
        val content: String,
        @SerializedName("server_time") val serverTime: Long
    )

    data class FriendListResponse(
        @SerializedName("user_id") val userId: Long,
        val nickname: String,
        val username: String = "",
        @SerializedName("avatar_url") val avatarUrl: String
    )

    data class UserSearchResult(
        @SerializedName("user_id") val userId: Long,
        val username: String,
        val nickname: String,
        @SerializedName("avatar_url") val avatarUrl: String = ""
    )

    data class GroupMemberResponse(
        @SerializedName("user_id") val userId: Long,
        val username: String = "",
        val nickname: String = "",
        val role: Int = 0
    )

    // --- Original API methods ---

    suspend fun register(username: String, password: String, nickname: String): AuthResponse =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(RegisterRequest(username, password, nickname))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$baseUrl/api/register")
                .post(body)
                .build()
            execute(request, AuthResponse::class.java)
        }

    suspend fun login(username: String, password: String): AuthResponse =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(LoginRequest(username, password))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$baseUrl/api/login")
                .post(body)
                .build()
            execute(request, AuthResponse::class.java)
        }

    // --- Phase 2 API methods ---

    suspend fun uploadFile(token: String, fileBytes: ByteArray, fileName: String, mimeType: String): FileUploadResponse =
        withContext(Dispatchers.IO) {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", fileName,
                    fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                )
                .build()
            val request = Request.Builder()
                .url("$baseUrl/api/file/upload")
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()
            parseApiData(executeRaw(request), FileUploadResponse::class.java)
        }

    suspend fun getFriendRequests(token: String): List<FriendRequestItem> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/friend/requests")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            parseApiDataList(executeRaw(request), FriendRequestItem::class.java)
        }

    suspend fun acceptFriend(token: String, fromUserId: Long) =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(FriendActionRequest(fromUserId))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$baseUrl/api/friend/accept")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            checkApiSuccess(executeRaw(request))
        }

    suspend fun applyFriend(token: String, targetUserId: Long, message: String = "") =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(FriendApplyRequest(targetUserId, message))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$baseUrl/api/friend/apply")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            checkApiSuccess(executeRaw(request))
        }

    suspend fun rejectFriend(token: String, fromUserId: Long) =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(FriendActionRequest(fromUserId))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$baseUrl/api/friend/reject")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            checkApiSuccess(executeRaw(request))
        }

    suspend fun getFriendList(token: String): List<FriendListResponse> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/friend/list")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            parseApiDataList(executeRaw(request), FriendListResponse::class.java)
        }

    suspend fun createGroup(token: String, name: String, memberIds: List<Long>): GroupInfoResponse =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(GroupCreateRequest(name, memberIds))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$baseUrl/api/group/create")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            parseApiData(executeRaw(request), GroupInfoResponse::class.java)
        }

    suspend fun inviteToGroup(token: String, groupId: Long, userIds: List<Long>) =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(GroupInviteRequest(groupId, userIds))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$baseUrl/api/group/invite")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            checkApiSuccess(executeRaw(request))
        }

    suspend fun kickFromGroup(token: String, groupId: Long, userId: Long) =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(GroupKickRequest(groupId, userId))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$baseUrl/api/group/kick")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            checkApiSuccess(executeRaw(request))
        }

    suspend fun dissolveGroup(token: String, groupId: Long) =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(GroupDissolveRequest(groupId))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$baseUrl/api/group/dissolve")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            checkApiSuccess(executeRaw(request))
        }

    suspend fun recallMessage(token: String, msgId: Long, sessionId: String) =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(MessageRecallRequest(msgId, sessionId))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$baseUrl/api/message/recall")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            checkApiSuccess(executeRaw(request))
        }

    suspend fun markMessageRead(token: String, sessionId: String, lastReadSeq: Long) =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(MessageReadRequest(sessionId, lastReadSeq))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$baseUrl/api/message/read")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            checkApiSuccess(executeRaw(request))
        }

    suspend fun searchMessages(token: String, keyword: String, sessionId: String, limit: Int = 20): List<MessageSearchResult> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/message/search?q=${java.net.URLEncoder.encode(keyword, "UTF-8")}&sessionId=${java.net.URLEncoder.encode(sessionId, "UTF-8")}&limit=$limit")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            parseApiDataList(executeRaw(request), MessageSearchResult::class.java)
        }

    suspend fun searchUsers(token: String, query: String, limit: Int = 20): List<UserSearchResult> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/user/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=$limit")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            parseApiDataList(executeRaw(request), UserSearchResult::class.java)
        }

    suspend fun getGroupMembers(token: String, groupId: Long): List<GroupMemberResponse> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/group/$groupId/members")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            parseApiDataList(executeRaw(request), GroupMemberResponse::class.java)
        }

    // --- Internal helpers ---

    private inline fun <reified T> execute(request: Request, clazz: Class<T>): T {
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response body")

        if (!response.isSuccessful) {
            Log.e(TAG, "HTTP ${response.code}: $responseBody")
            throw RuntimeException("HTTP ${response.code}: $responseBody")
        }

        return gson.fromJson(responseBody, clazz)
    }

    private fun executeRaw(request: Request): String {
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response body")

        if (!response.isSuccessful) {
            Log.e(TAG, "HTTP ${response.code}: $responseBody")
            throw RuntimeException("HTTP ${response.code}: $responseBody")
        }

        return responseBody
    }

    private fun <T> parseApiData(body: String, dataClass: Class<T>): T {
        val root = gson.fromJson(body, JsonObject::class.java)
        val code = root.get("code")?.asInt ?: throw RuntimeException("Missing code field")
        val msg = root.get("msg")?.asString ?: "Unknown error"
        if (code != 0) throw RuntimeException(msg)
        val dataElement = root.get("data") ?: throw RuntimeException("No data in response: $msg")
        return gson.fromJson(dataElement, dataClass)
    }

    private fun <T> parseApiDataList(body: String, elementClass: Class<T>): List<T> {
        val root = gson.fromJson(body, JsonObject::class.java)
        val code = root.get("code")?.asInt ?: throw RuntimeException("Missing code field")
        val msg = root.get("msg")?.asString ?: "Unknown error"
        if (code != 0) throw RuntimeException(msg)
        val dataElement = root.get("data") ?: return emptyList()
        val array = dataElement.asJsonArray
        return array.map { gson.fromJson(it, elementClass) }
    }

    private fun checkApiSuccess(body: String) {
        val root = gson.fromJson(body, JsonObject::class.java)
        val code = root.get("code")?.asInt ?: throw RuntimeException("Missing code field")
        val msg = root.get("msg")?.asString ?: "Unknown error"
        if (code != 0) throw RuntimeException(msg)
    }
}
