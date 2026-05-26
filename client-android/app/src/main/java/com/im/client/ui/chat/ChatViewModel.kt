package com.im.client.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import com.im.client.data.repository.AuthRepository
import com.im.client.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val chatRepository: ChatRepository
        get() = App.instance.chatRepository
    private val authRepository: AuthRepository
        get() = App.instance.authRepository
    private val tcpConnection: TcpConnection
        get() = App.instance.tcpConnection

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Message>>(emptyList())
    val searchResults: StateFlow<List<Message>> = _searchResults.asStateFlow()

    var inputText by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var searchMode by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set

    private var sessionId: String = ""

    fun init(sessionId: String) {
        if (this.sessionId == sessionId) return
        this.sessionId = sessionId
        viewModelScope.launch {
            chatRepository.getMessagesFlow(sessionId).collect { list ->
                _messages.value = list
            }
        }
        markReadViaApi()
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

    fun pickImage(context: Context, uri: Uri) {
        uploadAndSend(context, uri, "image/*")
    }

    fun pickFile(context: Context, uri: Uri) {
        uploadAndSend(context, uri, "*/*")
    }

    private fun uploadAndSend(context: Context, uri: Uri, mimeTypeFilter: String) {
        viewModelScope.launch {
            isLoading = true
            try {
                val token = authRepository.getToken(context) ?: return@launch
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val fileName = getFileName(context, uri)
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@launch

                val isGroup = sessionId.startsWith("group_")
                val targetId = sessionId.split("_").lastOrNull()?.toLongOrNull() ?: return@launch

                val result = chatRepository.uploadAndSendFile(
                    token, bytes, fileName, mimeType, targetId, isGroup
                )
                if (result.isFailure) {
                    // Error handled silently; could add error state
                }
            } catch (e: Exception) {
                // Upload failed
            } finally {
                isLoading = false
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    fun recallMessage(context: Context, msgId: Long, sid: String) {
        viewModelScope.launch {
            val token = authRepository.getToken(context) ?: return@launch
            chatRepository.recallMessage(token, msgId, sid)
        }
    }

    private fun markReadViaApi() {
        viewModelScope.launch {
            val context = App.instance.applicationContext
            val token = authRepository.getToken(context) ?: return@launch
            chatRepository.markReadViaApi(token, sessionId)
        }
    }

    fun toggleSearchMode() {
        searchMode = !searchMode
        if (!searchMode) {
            searchQuery = ""
            _searchResults.value = emptyList()
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            val context = App.instance.applicationContext
            val token = authRepository.getToken(context)
            if (token != null) {
                val results = chatRepository.searchMessagesViaApi(token, query, sessionId)
                _searchResults.value = results
            } else {
                chatRepository.searchMessagesFlow(sessionId, query).collect { results ->
                    _searchResults.value = results
                }
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
