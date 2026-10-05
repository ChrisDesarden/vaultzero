package com.vaultzero.app.presentation.unlock

import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzero.app.domain.model.UnlockResult
import com.vaultzero.app.domain.model.VaultSettings
import com.vaultzero.app.domain.usecase.CreateVaultUseCase
import com.vaultzero.app.domain.usecase.GetSettingsUseCase
import com.vaultzero.app.domain.usecase.IsUnlockedUseCase
import com.vaultzero.app.domain.usecase.IsVaultCreatedUseCase
import com.vaultzero.app.domain.usecase.UnlockVaultUseCase
import com.vaultzero.app.domain.usecase.UnlockWithBiometricUseCase
import com.vaultzero.app.presentation.biometric.BiometricHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val isVaultCreated: IsVaultCreatedUseCase,
    private val unlockVault: UnlockVaultUseCase,
    private val createVault: CreateVaultUseCase,
    private val isUnlocked: IsUnlockedUseCase,
    private val unlockWithBiometric: UnlockWithBiometricUseCase,
    private val biometricHelper: BiometricHelper,
    private val getSettings: GetSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val exists = isVaultCreated()
            val biometricsAvailable = biometricHelper.isAvailable()
            val settings = try { getSettings().first() } catch (_: Exception) { VaultSettings() }
            _uiState.update {
                it.copy(
                    isFirstRun = !exists,
                    biometricAvailable = biometricsAvailable,
                    biometricEnabled = settings.biometricEnabled && biometricsAvailable,
                    isLoading = false
                )
            }
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun onConfirmPasswordChanged(password: String) {
        _uiState.update { it.copy(confirmPassword = password, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.password.isBlank()) {
            _uiState.update { it.copy(error = "Password cannot be empty") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = if (state.isFirstRun) {
                if (state.password != state.confirmPassword) {
                    UnlockResult.Error("Passwords do not match")
                } else {
                    createVault(state.password)
                }
            } else {
                unlockVault(state.password)
            }
            when (result) {
                is UnlockResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, unlocked = true) }
                }
                is UnlockResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    fun biometricUnlock(activity: FragmentActivity, onResult: (Boolean, String?) -> Unit) {
        Log.d("UnlockViewModel", "biometricUnlock available=${biometricHelper.isAvailable()}")
        if (!biometricHelper.isAvailable()) {
            onResult(false, "Biometric unlock is not available")
            return
        }
        biometricHelper.prompt(
            activity,
            onSuccess = {
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                    val result = unlockWithBiometric()
                    when (result) {
                        is UnlockResult.Success -> {
                            _uiState.update { it.copy(isLoading = false, unlocked = true) }
                            onResult(true, null)
                        }
                        is UnlockResult.Error -> {
                            _uiState.update { it.copy(isLoading = false, error = result.message) }
                            onResult(false, result.message)
                        }
                    }
                }
            },
            onError = { msg ->
                _uiState.update { it.copy(error = msg) }
                onResult(false, msg)
            }
        )
    }

    data class UnlockUiState(
        val password: String = "",
        val confirmPassword: String = "",
        val isLoading: Boolean = true,
        val isFirstRun: Boolean = false,
        val unlocked: Boolean = false,
        val biometricAvailable: Boolean = false,
        val biometricEnabled: Boolean = false,
        val error: String? = null
    )
}
