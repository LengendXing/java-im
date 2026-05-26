package com.im.client.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val SESSIONS = "sessions"
    const val CHAT = "chat/{sessionId}"

    fun chat(sessionId: String) = "chat/$sessionId"
}
