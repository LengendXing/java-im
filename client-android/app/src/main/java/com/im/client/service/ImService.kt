package com.im.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.im.client.App
import com.im.client.MainActivity
import com.im.client.R
import com.im.client.data.remote.Cmd
import com.im.client.data.remote.TcpConnection
import com.im.client.data.repository.ChatRepository
import kotlinx.coroutines.*

class ImService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenJob: Job? = null

    companion object {
        private const val TAG = "ImService"
        private const val CHANNEL_ID = "im_connection"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ImService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ImService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Connected"))
        startListening()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scope.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startListening() {
        listenJob = scope.launch {
            val tcpConnection = App.instance.tcpConnection
            val chatRepository = App.instance.chatRepository
            for (packet in tcpConnection.incomingPackets) {
                when (packet.cmd) {
                    Cmd.C2C_MSG_NOTIFY -> {
                        chatRepository.handleC2CNotify(packet)
                        updateNotification("New message received")
                    }
                    Cmd.GROUP_MSG_NOTIFY -> {
                        chatRepository.handleGroupNotify(packet)
                        updateNotification("New group message")
                    }
                    Cmd.KICKOFF -> {
                        Log.w(TAG, "Kicked off by server")
                        updateNotification("Disconnected - kicked off")
                    }
                }
            }
        }

        // Observe connection state
        scope.launch {
            App.instance.tcpConnection.connectionState.collect { state ->
                val label = when (state) {
                    TcpConnection.ConnectionState.AUTHENTICATED -> "Connected"
                    TcpConnection.ConnectionState.CONNECTED -> "Connecting..."
                    TcpConnection.ConnectionState.CONNECTING -> "Connecting..."
                    TcpConnection.ConnectionState.DISCONNECTED -> "Disconnected"
                }
                updateNotification(label)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IM Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows IM connection status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("IM Client")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("IM Client")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }
}
