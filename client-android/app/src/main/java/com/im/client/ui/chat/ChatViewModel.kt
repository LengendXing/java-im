package com.im.client.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.im.client.App
import com.im.client.data.model.Message
import com.im.client.data.model.SessionType
import com.im.client.data.remote.Cmd
import com.im.client.data.remote.TcpConnection
import com.im.client.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val chatRepository: ChatRepository
        get() = App.instance.chatRepository
    private val tcpConnection: TcpConnection
        get() = App.instance.tcpConnection

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    var inputText by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    private var sessionId: String = ""

    fun init(sessionId: String) {
        this.sessionId = sessionId
        viewModelScope.launch {
            chatRepository.getMessagesFlow(sessionId).collect { list ->
                _messages.value = list
            }
        }
        viewModelScope.launch {
            chatRepository.markRead(sessionId)
        }
    }

    fun onInputChange(value: String) {
        inputText = value
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            val result = parseSessionType(sessionId) { targetId, type ->
                when (type) {
                    SessionType.C2C -> chatRepository.sendMessage(targetId, text)
                    SessionType.GROUP -> chatRepository.sendGroupMessage(targetId, text)
                }
            }
            isLoading = false
            if (result.isSuccess) {
                inputText = ""
            }
        }
    }

    private suspend fun parseSessionType(
        sessionId: String,
        action: suspend (targetId: Long, type: SessionType) -> Result<Message>
    ): Result<Message> {
        val parts = sessionId.split("_")
        val type = if (parts.firstOrNull() == "group") SessionType.GROUP else SessionType.C2C
        val targetId = parts.lastOrNull()?.toLongOrNull() ?: 0L
        return action(targetId, type)
    }
}
