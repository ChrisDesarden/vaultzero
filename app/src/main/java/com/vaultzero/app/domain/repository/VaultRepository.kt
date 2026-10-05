package com.vaultzero.app.domain.repository

import android.net.Uri
import com.vaultzero.app.domain.model.AppTheme
import com.vaultzero.app.domain.model.AutoLockTimeout
import com.vaultzero.app.domain.model.PasswordGeneratorDefaults
import com.vaultzero.app.domain.model.UnlockResult
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.model.VaultSettings
import kotlinx.coroutines.flow.Flow

/**
 * Canonical repository interface for VaultZero.
 *
 * All real implementations MUST match this interface exactly.
 * Do NOT change method signatures without updating ALL implementations.
 *
 * Agent 3 (UI) contract:
 *  - Call [isVaultCreated] on app launch to determine first-run.
 *  - Call [createVault] on first-run to initialize the DB.
 *  - Call [unlock] with the master password to decrypt the DB key and open the DB.
 *  - Call [lock] on background/timeout; this wipes the master key and invalidates DAOs.
 *  - Observe [isUnlocked] to drive UI state (locked/unlocked).
 */
interface VaultRepository {
    // -- Vault lifecycle --
    suspend fun createVault(password: String): UnlockResult
    suspend fun isVaultCreated(): Boolean
    suspend fun unlock(password: String): UnlockResult
    suspend fun unlockWithBiometric(): UnlockResult
    suspend fun isUnlocked(): Boolean
    suspend fun lock()

    val isUnlocked: Flow<Boolean>

    // -- Entries --
    val entries: Flow<List<VaultEntry>>
    fun getEntries(groupId: String? = null): Flow<List<VaultEntry>>
    suspend fun getEntry(entryId: String): VaultEntry?
    suspend fun saveEntry(entry: VaultEntry)
    suspend fun deleteEntry(entryId: String)
    fun searchEntries(query: String): Flow<List<VaultEntry>>

    // -- Groups --
    val groups: Flow<List<VaultGroup>>
    suspend fun saveGroup(group: VaultGroup)
    suspend fun deleteGroup(groupId: String)

    // -- Settings (DataStore-backed, granular) --
    val theme: Flow<AppTheme>
    val autoLockTimeout: Flow<AutoLockTimeout>
    val passwordDefaults: Flow<PasswordGeneratorDefaults>
    val biometricEnabled: Flow<Boolean>

    suspend fun setTheme(theme: AppTheme)
    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout)
    suspend fun setPasswordDefaults(defaults: PasswordGeneratorDefaults)
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun enrollBiometric(): Boolean
    suspend fun disableBiometric()

    // -- Settings (legacy aggregate for backward compat) --
    fun getSettings(): Flow<VaultSettings>
    suspend fun saveSettings(settings: VaultSettings)

    // -- Biometric bridge --
    suspend fun setBiometricKeyEncryptedDbKey(encryptedDbKey: ByteArray)
    suspend fun getBiometricKeyEncryptedDbKey(): ByteArray?
    suspend fun clearBiometricKey()

    // -- Import / Export --
    suspend fun exportVault(uri: Uri, password: String): Boolean
    suspend fun importVault(uri: Uri, password: String): Boolean
    suspend fun importPasswordSafe(uri: Uri, password: String): Boolean
}
