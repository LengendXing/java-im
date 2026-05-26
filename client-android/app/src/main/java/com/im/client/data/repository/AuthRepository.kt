package com.im.client.data.repository

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.im.client.data.local.AppDatabase
import com.im.client.data.remote.ApiService
import com.im.client.data.remote.Cmd
import com.im.client.data.remote.MsgType
import com.im.client.data.remote.TcpConnection
import com.im.client.data.model.User
import com.im.protocol.ImProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val apiService: ApiService,
    private val tcpConnection: TcpConnection,
    private val database: AppDatabase
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

    companion object {
        private const val TAG = "AuthRepository"
        private val KEY_TOKEN = stringPreferencesKey("jwt_token")
        private val KEY_USER_ID = longPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_NICKNAME = stringPreferencesKey("nickname")
        private val KEY_AVATAR_URL = stringPreferencesKey("avatar_url")
    }

    suspend fun login(context: Context, username: String, password: String): Result<User> {
        return try {
            val resp = apiService.login(username, password)
            if (resp.code != 0) {
                return Result.failure(Exception(resp.msg))
            }
            val user = User(
                userId = resp.userId,
                username = resp.username,
                nickname = resp.nickname,
                avatarUrl = resp.avatarUrl ?: ""
            )
            saveUser(context, user, resp.token)
            connectAndAuthenticate(context, resp.token)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Login failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun register(context: Context, username: String, password: String, nickname: String): Result<User> {
        return try {
            val resp = apiService.register(username, password, nickname)
            if (resp.code != 0) {
                return Result.failure(Exception(resp.msg))
            }
            val user = User(
                userId = resp.userId,
                username = resp.username,
                nickname = resp.nickname,
                avatarUrl = resp.avatarUrl ?: ""
            )
            saveUser(context, user, resp.token)
            connectAndAuthenticate(context, resp.token)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Register failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun saveUser(context: Context, user: User, token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USER_ID] = user.userId
            prefs[KEY_USERNAME] = user.username
            prefs[KEY_NICKNAME] = user.nickname
            prefs[KEY_AVATAR_URL] = user.avatarUrl
        }
    }

    suspend fun getToken(context: Context): String? {
        var token: String? = null
        context.dataStore.edit { prefs -> token = prefs[KEY_TOKEN] }
        return token
    }

    fun getUserFlow(context: Context): Flow<User?> {
        return context.dataStore.data.map { prefs ->
            val userId = prefs[KEY_USER_ID] ?: return@map null
            User(
                userId = userId,
                username = prefs[KEY_USERNAME] ?: "",
                nickname = prefs[KEY_NICKNAME] ?: "",
                avatarUrl = prefs[KEY_AVATAR_URL] ?: ""
            )
        }
    }

    suspend fun getSavedUser(context: Context): User? {
        var user: User? = null
        context.dataStore.edit { prefs ->
            val userId = prefs[KEY_USER_ID] ?: return@edit
            user = User(
                userId = userId,
                username = prefs[KEY_USERNAME] ?: "",
                nickname = prefs[KEY_NICKNAME] ?: "",
                avatarUrl = prefs[KEY_AVATAR_URL] ?: ""
            )
        }
        return user
    }

    private fun connectAndAuthenticate(context: Context, token: String) {
        val deviceId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        tcpConnection.connect()
        // Authentication will be sent once connection state becomes CONNECTED
        // via observing connectionState flow. Simplified here:
        tcpConnection.authenticate(token, deviceId, 2) // platform=2 for Android
    }

    suspend fun logout(context: Context) {
        context.dataStore.edit { it.clear() }
        tcpConnection.disconnect()
    }

    fun isLoggedInFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_TOKEN] != null
        }
    }
}
