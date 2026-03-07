package com.tanglycohort.greenflow.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanglycohort.greenflow.data.repository.AuthRepository
import com.tanglycohort.greenflow.util.PhoneUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val loading: Boolean = false,
    val error: String? = null
)

sealed class RegisterEvent {
    data object ShowCheckEmailMessage : RegisterEvent()
    data object NavigateToLogin : RegisterEvent()
    data class ShowError(val message: String) : RegisterEvent()
}

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RegisterEvent>()
    val events = _events.asSharedFlow()

    fun register(name: String, phone: String, email: String, password: String) {
        val normalizedPhone = PhoneUtils.normalizeAndValidate(phone)
        if (normalizedPhone == null) {
            viewModelScope.launch { _events.emit(RegisterEvent.ShowError("מספר טלפון לא תקין (10–15 ספרות)")) }
            return
        }
        if (password.length < 6) {
            viewModelScope.launch { _events.emit(RegisterEvent.ShowError("סיסמה לפחות 6 תווים")) }
            return
        }
        if (name.isBlank() || email.isBlank()) {
            viewModelScope.launch { _events.emit(RegisterEvent.ShowError("נא למלא את כל השדות")) }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            authRepository.signUp(email.trim(), password, name.trim(), normalizedPhone)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(loading = false)
                    _events.emit(RegisterEvent.ShowCheckEmailMessage)
                    _events.emit(RegisterEvent.NavigateToLogin)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(loading = false, error = e.message)
                    _events.emit(RegisterEvent.ShowError(e.message ?: "הרשמה נכשלה"))
                }
        }
    }
}
