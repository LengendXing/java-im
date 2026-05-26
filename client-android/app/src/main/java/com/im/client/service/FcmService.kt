package com.im.client.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.im.client.data.remote.TcpConnection
import com.im.protocol.ImProto
import com.im.client.data.remote.ImPacket
import com.im.client.data.remote.PacketCodec

class FcmService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "FcmService"

        fun registerPushToken(token: String) {
            val conn = TcpConnection.getInstance()
            val req = ImProto.PushTokenRegisterRequest.newBuilder()
                .setPlatform(2)
                .setToken(token)
                .setBundleId("com.im.client")
                .build()
            conn.sendRequest(0x0803, 1, req)
            Log.i(TAG, "Push token registered via TCP")
        }

        fun fetchAndRegister() {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (token != null) registerPushToken(token)
            }.addOnFailureListener { e ->
                Log.w(TAG, "FCM token fetch failed: ${e.message}")
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token refreshed")
        registerPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: "新消息"
        val body = message.notification?.body ?: "你有一条新消息"
        Log.d(TAG, "FCM received: title=$title")

        val helper = NotificationHelper(this)
        helper.showMessageNotification(title, body)
    }
}
