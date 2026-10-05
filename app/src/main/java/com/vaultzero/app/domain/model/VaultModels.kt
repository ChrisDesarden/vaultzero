package com.vaultzero.app.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

// ------------------------------------------------------------------
// Domain models — used by UI layer (Agent 3)
// These hold plaintext fields; the repository encrypts/decrypts at the boundary.
// ------------------------------------------------------------------

@Immutable
@Parcelize
data class VaultEntry(
    val id: String,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val groupId: String? = null,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
) : Parcelable

@Immutable
@Parcelize
data class VaultGroup(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val icon: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
) : Parcelable

// ------------------------------------------------------------------
// Settings & UI models
// ------------------------------------------------------------------

enum class AppTheme { LIGHT, DARK, SYSTEM }

enum class AutoLockTimeout(val seconds: Int, val label: String) {
    IMMEDIATE(0, "Immediate"),
    THIRTY_SECONDS(30, "30 seconds"),
    ONE_MINUTE(60, "1 minute"),
    FIVE_MINUTES(300, "5 minutes"),
    FIFTEEN_MINUTES(900, "15 minutes"),
    THIRTY_MINUTES(1800, "30 minutes"),
    NEVER(-1, "Never")
}

@Immutable
data class PasswordGeneratorDefaults(
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = false
)

// Kept for backward compat with any UI code referencing it
@Immutable
data class VaultSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.FIVE_MINUTES,
    val passwordLength: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = true,
    val clipboardClearSeconds: Int = 30,
    val biometricEnabled: Boolean = false
)

// ------------------------------------------------------------------
// Result types
// ------------------------------------------------------------------

sealed class UnlockResult {
    data object Success : UnlockResult()
    data class Error(val message: String) : UnlockResult()
}
