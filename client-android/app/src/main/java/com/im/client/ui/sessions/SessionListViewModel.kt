package com.im.client.ui.sessions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.im.client.App
import com.im.client.data.model.Session
import com.im.client.data.remote.Cmd
import com.im.client.data.remote.TcpConnection
import com.im.client.data.repository.ChatRepository
import com.im.client.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionListViewModel : ViewModel() {

    private val sessionRepository: SessionRepository
        get() = App.instance.sessionRepository
    private val chatRepository: ChatRepository
        get() = App.instance.chatRepository
    private val tcpConnection: TcpConnection
        get() = App.instance.tcpConnection

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    var isRefreshing by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            sessionRepository.getAllSessionsFlow().collect { list ->
                _sessions.value = list
            }
        }
        refresh()
        startListeningNotifies()
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            sessionRepository.refreshSessions()
            isRefreshing = false
        }
    }

    private fun startListeningNotifies() {
        viewModelScope.launch {
            for (packet in tcpConnection.incomingPackets) {
                when (packet.cmd) {
                    Cmd.C2C_MSG_NOTIFY -> chatRepository.handleC2CNotify(packet)
                    Cmd.GROUP_MSG_NOTIFY -> chatRepository.handleGroupNotify(packet)
                }
            }
        }
    }
}
