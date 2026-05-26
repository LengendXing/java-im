package com.im.client.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiService private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
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
        val token: String
    )

    data class ApiResponse<T>(
        val code: Int,
        val msg: String,
        val data: T?
    )

    // --- API methods ---

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
}
