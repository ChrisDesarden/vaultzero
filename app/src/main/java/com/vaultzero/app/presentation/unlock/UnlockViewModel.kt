package com.vaultzero.app.presentation.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzero.app.biometric.BiometricHelper
import com.vaultzero.app.domain.model.UnlockResult
import com.vaultzero.app.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val biometricHelper: BiometricHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UnlockEvent>()
    val events: SharedFlow<UnlockEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val vaultExists = repository.isVaultCreated()
            val biometricAvailable = biometricHelper.canAuthenticate()
            _uiState.value = _uiState.value.copy(
                isCreateMode = !vaultExists,
                showBiometricButton = vaultExists && biometricAvailable
            )
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun submit() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val state = _uiState.value
            val result = if (state.isCreateMode) {
                repository.createVault(state.password)
            } else {
                repository.unlock(state.password)
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
            when (result) {
                is UnlockResult.Success -> _events.emit(UnlockEvent.Unlocked)
                is UnlockResult.Error -> _uiState.value = _uiState.value.copy(error = result.message)
            }
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            _events.emit(UnlockEvent.Unlocked)
        }
    }

    data class UnlockUiState(
        val password: String = "",
        val isLoading: Boolean = false,
        val isCreateMode: Boolean = false,
        val showBiometricButton: Boolean = false,
        val error: String? = null
    )

    sealed class UnlockEvent {
        data object Unlocked : UnlockEvent()
    }
}
