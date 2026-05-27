package com.im.client.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val SESSIONS = "sessions"
    const val CHAT = "chat/{sessionId}"
    const val GROUP_CREATE = "group_create"
    const val GROUP_MANAGE = "group_manage/{groupId}"
    const val GROUP_MEMBERS = "group_members/{groupId}"
    const val SETTINGS = "settings"

    fun chat(sessionId: String) = "chat/$sessionId"
    fun groupManage(groupId: Long) = "group_manage/$groupId"
    fun groupMembers(groupId: Long) = "group_members/$groupId"
}
