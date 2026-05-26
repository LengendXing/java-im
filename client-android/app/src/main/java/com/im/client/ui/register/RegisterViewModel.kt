package com.im.client.ui.register

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.im.client.App
import com.im.client.data.model.User
import com.im.client.data.repository.AuthRepository
import kotlinx.coroutines.launch

class RegisterViewModel : ViewModel() {

    var uiState by mutableStateOf(RegisterUiState())
        private set

    private val authRepository: AuthRepository
        get() = App.instance.authRepository

    fun onUsernameChange(value: String) {
        uiState = uiState.copy(username = value, error = null)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, error = null)
    }

    fun onConfirmPasswordChange(value: String) {
        uiState = uiState.copy(confirmPassword = value, error = null)
    }

    fun onNicknameChange(value: String) {
        uiState = uiState.copy(nickname = value, error = null)
    }

    fun register(onSuccess: (User) -> Unit) {
        if (uiState.username.isBlank() || uiState.password.isBlank() || uiState.nickname.isBlank()) {
            uiState = uiState.copy(error = "All fields are required")
            return
        }
        if (uiState.password != uiState.confirmPassword) {
            uiState = uiState.copy(error = "Passwords do not match")
            return
        }
        if (uiState.password.length < 6) {
            uiState = uiState.copy(error = "Password must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            val result = authRepository.register(
                App.instance, uiState.username, uiState.password, uiState.nickname
            )
            uiState = uiState.copy(isLoading = false)
            result.onSuccess { user -> onSuccess(user) }
                .onFailure { e -> uiState = uiState.copy(error = e.message) }
        }
    }
}

data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nickname: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
