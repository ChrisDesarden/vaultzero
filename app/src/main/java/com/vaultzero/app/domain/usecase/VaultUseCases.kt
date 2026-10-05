package com.vaultzero.app.domain.usecase

import android.net.Uri
import com.vaultzero.app.crypto.CryptoManager
import com.vaultzero.app.domain.model.AppTheme
import com.vaultzero.app.domain.model.AutoLockTimeout
import com.vaultzero.app.domain.model.PasswordGeneratorDefaults
import com.vaultzero.app.domain.model.UnlockResult
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.model.VaultSettings
import com.vaultzero.app.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use-case facade exposed to Agent 3 ViewModels.
 *
 * Each use case is a single-responsibility class that delegates to the
 * repository. They are grouped here for convenient constructor injection.
 */
class VaultUseCases @Inject constructor(
    val createVault: CreateVaultUseCase,
    val unlockVault: UnlockVaultUseCase,
    val unlockWithBiometric: UnlockWithBiometricUseCase,
    val lockVault: LockVaultUseCase,
    val isVaultCreated: IsVaultCreatedUseCase,
    val observeVaultLockState: ObserveVaultLockStateUseCase,
    val isUnlocked: IsUnlockedUseCase,
    val observeEntries: ObserveEntriesUseCase,
    val getEntries: GetEntriesUseCase,
    val getEntry: GetEntryUseCase,
    val saveEntry: SaveEntryUseCase,
    val deleteEntry: DeleteEntryUseCase,
    val searchEntries: SearchEntriesUseCase,
    val observeGroups: ObserveGroupsUseCase,
    val getGroups: GetGroupsUseCase,
    val saveGroup: SaveGroupUseCase,
    val deleteGroup: DeleteGroupUseCase,
    val getSettings: GetSettingsUseCase,
    val saveSettings: SaveSettingsUseCase,
    val setTheme: SetThemeUseCase,
    val setAutoLockTimeout: SetAutoLockTimeoutUseCase,
    val setPasswordDefaults: SetPasswordDefaultsUseCase,
    val setBiometricEnabled: SetBiometricEnabledUseCase,
    val generatePassword: GeneratePasswordUseCase,
    val exportVault: ExportVaultUseCase,
    val importVault: ImportVaultUseCase,
    val changeMasterPassword: ChangeMasterPasswordUseCase,
    val setBiometricKeyEncryptedDbKey: SetBiometricKeyEncryptedDbKeyUseCase,
    val getBiometricKeyEncryptedDbKey: GetBiometricKeyEncryptedDbKeyUseCase,
    val clearBiometricKey: ClearBiometricKeyUseCase
)

class CreateVaultUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(password: String): UnlockResult =
        repository.createVault(password)
}

class UnlockVaultUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(password: String): UnlockResult =
        repository.unlock(password)
}

class UnlockWithBiometricUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(): UnlockResult =
        repository.unlockWithBiometric()
}

class LockVaultUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke() = repository.lock()
}

class IsVaultCreatedUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(): Boolean = repository.isVaultCreated()
}

class ObserveVaultLockStateUseCase @Inject constructor(private val repository: VaultRepository) {
    operator fun invoke(): Flow<Boolean> = repository.isUnlocked
}

class IsUnlockedUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(): Boolean = repository.isUnlocked()
}

class ObserveEntriesUseCase @Inject constructor(private val repository: VaultRepository) {
    operator fun invoke(): Flow<List<VaultEntry>> = repository.entries
}

class GetEntriesUseCase @Inject constructor(private val repository: VaultRepository) {
    operator fun invoke(groupId: String? = null): Flow<List<VaultEntry>> =
        repository.getEntries(groupId)
}

class GetEntryUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(entryId: String): VaultEntry? =
        repository.getEntry(entryId)
}

class SaveEntryUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(entry: VaultEntry) = repository.saveEntry(entry)
}

class DeleteEntryUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(entryId: String) = repository.deleteEntry(entryId)
}

class SearchEntriesUseCase @Inject constructor(private val repository: VaultRepository) {
    operator fun invoke(query: String): Flow<List<VaultEntry>> =
        repository.searchEntries(query)
}

class ObserveGroupsUseCase @Inject constructor(private val repository: VaultRepository) {
    operator fun invoke(): Flow<List<VaultGroup>> = repository.groups
}

class GetGroupsUseCase @Inject constructor(private val repository: VaultRepository) {
    operator fun invoke(): Flow<List<VaultGroup>> = repository.groups
}

class SaveGroupUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(group: VaultGroup) = repository.saveGroup(group)
}

class DeleteGroupUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(groupId: String) = repository.deleteGroup(groupId)
}

class GetSettingsUseCase @Inject constructor(private val repository: VaultRepository) {
    operator fun invoke(): Flow<VaultSettings> = repository.getSettings()
}

class SaveSettingsUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(settings: VaultSettings) = repository.saveSettings(settings)
}

class SetThemeUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(theme: AppTheme) = repository.setTheme(theme)
}

class SetAutoLockTimeoutUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(timeout: AutoLockTimeout) = repository.setAutoLockTimeout(timeout)
}

class SetPasswordDefaultsUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(defaults: PasswordGeneratorDefaults) =
        repository.setPasswordDefaults(defaults)
}

class SetBiometricEnabledUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setBiometricEnabled(enabled)
}

class GeneratePasswordUseCase @Inject constructor(private val crypto: CryptoManager) {
    operator fun invoke(config: CryptoManager.PasswordConfig = CryptoManager.PasswordConfig()): CharArray =
        crypto.generatePassword(config)
}

class ExportVaultUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(uri: Uri, password: String): Boolean = repository.exportVault(uri, password)
}

class ImportVaultUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(uri: Uri, password: String): Boolean = repository.importVault(uri, password)
}

class ChangeMasterPasswordUseCase @Inject constructor(private val repository: VaultRepository) {
    /**
     * Re-creates the vault with [newPassword], preserving entries and groups.
     *
     * Current implementation is a stub; a real implementation would:
     *  1. Unlock with the old password.
     *  2. Export all entries/groups to plaintext in memory.
     *  3. Close and delete the old vault.
     *  4. Create a new vault with [newPassword].
     *  5. Re-insert all entries/groups.
     *  6. Clear biometric enrollment.
     */
    suspend operator fun invoke(newPassword: String): UnlockResult {
        // Stub: requires old password to be supplied in a real implementation.
        return repository.createVault(newPassword)
    }
}

class SetBiometricKeyEncryptedDbKeyUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(encryptedDbKey: ByteArray) =
        repository.setBiometricKeyEncryptedDbKey(encryptedDbKey)
}

class GetBiometricKeyEncryptedDbKeyUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(): ByteArray? = repository.getBiometricKeyEncryptedDbKey()
}

class ClearBiometricKeyUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke() = repository.clearBiometricKey()
}

class EnrollBiometricUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke(): Boolean = repository.enrollBiometric()
}

class DisableBiometricUseCase @Inject constructor(private val repository: VaultRepository) {
    suspend operator fun invoke() = repository.disableBiometric()
}
