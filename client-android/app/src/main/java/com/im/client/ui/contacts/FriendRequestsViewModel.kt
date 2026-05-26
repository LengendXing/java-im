package com.im.client.ui.contacts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.im.client.App
import com.im.client.data.model.FriendRequest
import com.im.client.data.repository.FriendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FriendRequestsViewModel : ViewModel() {

    private val friendRepository: FriendRepository
        get() = App.instance.friendRepository
    private val authRepository
        get() = App.instance.authRepository

    private val _requests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val requests: StateFlow<List<FriendRequest>> = _requests.asStateFlow()

    var isLoading by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            val context = App.instance.applicationContext
            val token = authRepository.getToken(context) ?: return@launch
            _requests.value = friendRepository.getFriendRequests(token)
            isLoading = false
        }
    }

    fun acceptRequest(fromUserId: Long) {
        viewModelScope.launch {
            val context = App.instance.applicationContext
            val token = authRepository.getToken(context) ?: return@launch
            val result = friendRepository.acceptFriend(token, fromUserId)
            if (result.isSuccess) {
                refresh()
            }
        }
    }

    fun rejectRequest(fromUserId: Long) {
        viewModelScope.launch {
            val context = App.instance.applicationContext
            val token = authRepository.getToken(context) ?: return@launch
            val result = friendRepository.rejectFriend(token, fromUserId)
            if (result.isSuccess) {
                refresh()
            }
        }
    }
}
