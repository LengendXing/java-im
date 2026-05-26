package com.im.client.ui.group

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.im.client.App
import com.im.client.data.remote.ApiService
import com.im.client.data.repository.FriendRepository
import com.im.client.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupCreateViewModel : ViewModel() {

    private val groupRepository: GroupRepository
        get() = App.instance.groupRepository
    private val friendRepository: FriendRepository
        get() = App.instance.friendRepository
    private val authRepository
        get() = App.instance.authRepository

    var groupName by mutableStateOf("")
        private set

    private val _friends = MutableStateFlow<List<ApiService.FriendListResponse>>(emptyList())
    val friends: StateFlow<List<ApiService.FriendListResponse>> = _friends.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    var isCreating by mutableStateOf(false)
        private set

    init {
        loadFriends()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            val context = App.instance.applicationContext
            val token = authRepository.getToken(context) ?: return@launch
            _friends.value = friendRepository.getFriendList(token)
        }
    }

    fun onGroupNameChange(name: String) {
        groupName = name
    }

    fun toggleMember(userId: Long) {
        _selectedIds.value = if (_selectedIds.value.contains(userId)) {
            _selectedIds.value - userId
        } else {
            _selectedIds.value + userId
        }
    }

    fun createGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            isCreating = true
            val context = App.instance.applicationContext
            val token = authRepository.getToken(context) ?: return@launch
            val result = groupRepository.createGroup(token, groupName, _selectedIds.value.toList())
            isCreating = false
            if (result.isSuccess) {
                onSuccess()
            }
        }
    }
}
