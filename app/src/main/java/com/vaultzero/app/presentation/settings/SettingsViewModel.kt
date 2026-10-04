package com.vaultzero.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzero.app.biometric.BiometricHelper
import com.vaultzero.app.domain.model.AppTheme
import com.vaultzero.app.domain.model.AutoLockTimeout
import com.vaultzero.app.domain.model.PasswordGeneratorDefaults
import com.vaultzero.app.domain.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val biometricHelper: BiometricHelper
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.theme,
        repository.autoLockTimeout,
        repository.passwordDefaults,
        repository.biometricEnabled,
        kotlinx.coroutines.flow.flowOf(biometricHelper.canAuthenticate())
    ) { theme, timeout, defaults, biometricEnabled, biometricAvailable ->
        SettingsUiState(
            theme = theme,
            autoLockTimeout = timeout,
            passwordDefaults = defaults,
            biometricEnabled = biometricEnabled,
            biometricAvailable = biometricAvailable
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    private val _events = MutableStateFlow<SettingsEvent?>(null)
    val events: StateFlow<SettingsEvent?> = _events.asStateFlow()

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { repository.setTheme(theme) }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        viewModelScope.launch { repository.setAutoLockTimeout(timeout) }
    }

    fun setPasswordLength(length: Int) {
        viewModelScope.launch {
            val current = uiState.value.passwordDefaults
            repository.setPasswordDefaults(current.copy(length = length.coerceIn(4, 128)))
        }
    }

    fun togglePasswordOption(option: PasswordOption, enabled: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.passwordDefaults
            repository.setPasswordDefaults(
                when (option) {
                    PasswordOption.UPPERCASE -> current.copy(includeUppercase = enabled)
                    PasswordOption.LOWERCASE -> current.copy(includeLowercase = enabled)
                    PasswordOption.NUMBERS -> current.copy(includeNumbers = enabled)
                    PasswordOption.SYMBOLS -> current.copy(includeSymbols = enabled)
                    PasswordOption.AMBIGUOUS -> current.copy(excludeAmbiguous = enabled)
                }
            )
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !biometricHelper.canAuthenticate()) {
                _events.value = SettingsEvent.BiometricUnavailable
                return@launch
            }
            repository.setBiometricEnabled(enabled)
            if (!enabled) {
                repository.clearBiometricKey()
            }
            _events.value = SettingsEvent.BiometricChanged(enabled)
        }
    }

    fun exportVault() {
        viewModelScope.launch {
            _events.value = SettingsEvent.ExportRequested
        }
    }

    fun importVault() {
        viewModelScope.launch {
            _events.value = SettingsEvent.ImportRequested
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    data class SettingsUiState(
        val theme: AppTheme = AppTheme.SYSTEM,
        val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.FIVE_MINUTES,
        val passwordDefaults: PasswordGeneratorDefaults = PasswordGeneratorDefaults(),
        val biometricEnabled: Boolean = false,
        val biometricAvailable: Boolean = false
    )

    sealed class SettingsEvent {
        data class BiometricChanged(val enabled: Boolean) : SettingsEvent()
        data object BiometricUnavailable : SettingsEvent()
        data object ExportRequested : SettingsEvent()
        data object ImportRequested : SettingsEvent()
    }

    enum class PasswordOption { UPPERCASE, LOWERCASE, NUMBERS, SYMBOLS, AMBIGUOUS }
}
