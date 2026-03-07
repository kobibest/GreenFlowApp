package com.tanglycohort.greenflow.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanglycohort.greenflow.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null
)

sealed class LoginEvent {
    data object NavigateToDashboard : LoginEvent()
    data class ShowError(val message: String) : LoginEvent()
}

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>(replay = 1)
    val events = _events.asSharedFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            viewModelScope.launch { _events.emit(LoginEvent.ShowError("נא למלא אימייל וסיסמה")) }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            authRepository.signIn(email.trim(), password)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(loading = false)
                    _events.emit(LoginEvent.NavigateToDashboard)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(loading = false, error = e.message)
                    _events.emit(LoginEvent.ShowError(e.message ?: "התחברות נכשלה"))
                }
        }
    }
}
