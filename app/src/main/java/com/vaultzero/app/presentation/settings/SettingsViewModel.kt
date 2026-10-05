package com.vaultzero.app.presentation.settings

import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzero.app.domain.model.AppTheme
import com.vaultzero.app.domain.model.AutoLockTimeout
import com.vaultzero.app.domain.model.PasswordGeneratorDefaults
import com.vaultzero.app.domain.model.VaultSettings
import com.vaultzero.app.domain.usecase.DisableBiometricUseCase
import com.vaultzero.app.domain.usecase.ExportVaultUseCase
import com.vaultzero.app.domain.usecase.ImportPasswordSafeUseCase
import com.vaultzero.app.domain.usecase.ImportVaultUseCase
import com.vaultzero.app.domain.usecase.EnrollBiometricUseCase
import com.vaultzero.app.domain.usecase.GetSettingsUseCase
import com.vaultzero.app.domain.usecase.SaveSettingsUseCase
import com.vaultzero.app.domain.usecase.SetAutoLockTimeoutUseCase
import com.vaultzero.app.domain.usecase.SetPasswordDefaultsUseCase
import com.vaultzero.app.domain.usecase.SetThemeUseCase
import com.vaultzero.app.presentation.biometric.BiometricHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettings: GetSettingsUseCase,
    private val saveSettings: SaveSettingsUseCase,
    private val setTheme: SetThemeUseCase,
    private val setAutoLockTimeout: SetAutoLockTimeoutUseCase,
    private val setPasswordDefaults: SetPasswordDefaultsUseCase,
    private val enrollBiometric: EnrollBiometricUseCase,
    private val disableBiometric: DisableBiometricUseCase,
    private val exportVault: ExportVaultUseCase,
    private val importVault: ImportVaultUseCase,
    private val importPasswordSafe: ImportPasswordSafeUseCase,
    private val biometricHelper: BiometricHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getSettings().collect { settings ->
                _uiState.update {
                    it.copy(
                        theme = settings.theme,
                        autoLockTimeout = settings.autoLockTimeout,
                        passwordDefaults = PasswordGeneratorDefaults(
                            length = settings.passwordLength,
                            includeUppercase = settings.includeUppercase,
                            includeLowercase = settings.includeLowercase,
                            includeNumbers = settings.includeDigits,
                            includeSymbols = settings.includeSymbols,
                            excludeAmbiguous = settings.excludeAmbiguous
                        ),
                        biometricAvailable = biometricHelper.isAvailable(),
                        biometricEnabled = settings.biometricEnabled
                    )
                }
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { setTheme.invoke(theme) }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        viewModelScope.launch { setAutoLockTimeout.invoke(timeout) }
    }

    fun setPasswordLength(length: Int) {
        viewModelScope.launch {
            val current = _uiState.value.passwordDefaults
            setPasswordDefaults(current.copy(length = length.coerceIn(4, 128)))
        }
    }

    fun togglePasswordOption(option: PasswordOption, enabled: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.passwordDefaults
            val updated = when (option) {
                PasswordOption.UPPERCASE -> current.copy(includeUppercase = enabled)
                PasswordOption.LOWERCASE -> current.copy(includeLowercase = enabled)
                PasswordOption.NUMBERS -> current.copy(includeNumbers = enabled)
                PasswordOption.SYMBOLS -> current.copy(includeSymbols = enabled)
                PasswordOption.AMBIGUOUS -> current.copy(excludeAmbiguous = enabled)
            }
            setPasswordDefaults(updated)
        }
    }

    fun saveLegacySettings() {
        viewModelScope.launch {
            val s = _uiState.value
            saveSettings(
                VaultSettings(
                    theme = s.theme,
                    autoLockTimeout = s.autoLockTimeout,
                    passwordLength = s.passwordDefaults.length,
                    includeUppercase = s.passwordDefaults.includeUppercase,
                    includeLowercase = s.passwordDefaults.includeLowercase,
                    includeDigits = s.passwordDefaults.includeNumbers,
                    includeSymbols = s.passwordDefaults.includeSymbols,
                    excludeAmbiguous = s.passwordDefaults.excludeAmbiguous
                )
            )
        }
    }

    fun setBiometricEnabled(activity: FragmentActivity, enabled: Boolean, onResult: (String?) -> Unit) {
        Log.d("SettingsViewModel", "setBiometricEnabled enabled=$enabled available=${biometricHelper.isAvailable()}")
        viewModelScope.launch {
            if (!biometricHelper.isAvailable()) {
                onResult("Biometric unlock is not available on this device")
                return@launch
            }
            if (enabled) {
                biometricHelper.prompt(
                    activity,
                    onSuccess = {
                        viewModelScope.launch {
                            val ok = enrollBiometric()
                            onResult(if (ok) null else "Failed to enroll biometric unlock")
                        }
                    },
                    onError = { msg -> onResult(msg) }
                )
            } else {
                disableBiometric()
                onResult(null)
            }
        }
    }

    fun exportVault(uri: android.net.Uri, password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val ok = exportVault.invoke(uri, password)
            onResult(if (ok) null else "Export failed")
        }
    }

    fun importVault(uri: android.net.Uri, password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val ok = importVault.invoke(uri, password)
            onResult(if (ok) null else "Import failed: wrong password or bad file")
        }
    }


    fun importPasswordSafe(uri: android.net.Uri, password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val ok = importPasswordSafe.invoke(uri, password)
            onResult(if (ok) null else "Import failed: wrong PasswordSafe password or unsupported file")
        }
    }

    data class SettingsUiState(
        val theme: AppTheme = AppTheme.SYSTEM,
        val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.FIVE_MINUTES,
        val passwordDefaults: PasswordGeneratorDefaults = PasswordGeneratorDefaults(),
        val biometricAvailable: Boolean = false,
        val biometricEnabled: Boolean = false
    )

    enum class PasswordOption { UPPERCASE, LOWERCASE, NUMBERS, SYMBOLS, AMBIGUOUS }
}
