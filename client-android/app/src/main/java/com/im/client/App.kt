package com.im.client

import android.app.Application
import com.im.client.data.local.AppDatabase
import com.im.client.data.remote.ApiService
import com.im.client.data.remote.TcpConnection
import com.im.client.data.repository.AuthRepository
import com.im.client.data.repository.ChatRepository
import com.im.client.data.repository.FriendRepository
import com.im.client.data.repository.GroupRepository
import com.im.client.data.repository.SessionRepository

class App : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val apiService by lazy { ApiService.getInstance() }
    val tcpConnection by lazy { TcpConnection.getInstance() }

    val authRepository by lazy {
        AuthRepository(apiService, tcpConnection, database)
    }
    val chatRepository by lazy {
        ChatRepository(tcpConnection, database, apiService)
    }
    val sessionRepository by lazy {
        SessionRepository(apiService, tcpConnection, database)
    }
    val friendRepository by lazy {
        FriendRepository(apiService)
    }
    val groupRepository by lazy {
        GroupRepository(apiService)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
