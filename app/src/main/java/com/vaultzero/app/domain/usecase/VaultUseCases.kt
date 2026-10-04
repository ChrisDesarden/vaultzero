package com.vaultzero.app.domain.usecase

import com.vaultzero.app.crypto.CryptoManager
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CreateVaultUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(password: String) = repo.createVault(password)
}

class UnlockVaultUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(password: String) = repo.unlock(password)
}

class LockVaultUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke() = repo.lock()
}

class IsVaultCreatedUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke() = repo.isVaultCreated()
}

class ObserveVaultLockStateUseCase @Inject constructor(private val repo: VaultRepository) {
    operator fun invoke(): Flow<Boolean> = repo.isUnlocked
}

class ObserveEntriesUseCase @Inject constructor(private val repo: VaultRepository) {
    operator fun invoke(): Flow<List<VaultEntry>> = repo.entries
}

class ObserveGroupsUseCase @Inject constructor(private val repo: VaultRepository) {
    operator fun invoke(): Flow<List<VaultGroup>> = repo.groups
}

class GetEntryUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(entryId: String): VaultEntry? = repo.getEntry(entryId)
}

class SaveEntryUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(entry: VaultEntry) = repo.saveEntry(entry)
}

class DeleteEntryUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(entryId: String) = repo.deleteEntry(entryId)
}

class SaveGroupUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(group: VaultGroup) = repo.saveGroup(group)
}

class DeleteGroupUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(groupId: String) = repo.deleteGroup(groupId)
}

class SearchEntriesUseCase @Inject constructor(private val repo: VaultRepository) {
    operator fun invoke(query: String): Flow<List<VaultEntry>> = repo.searchEntries(query)
}

class GeneratePasswordUseCase @Inject constructor(private val crypto: CryptoManager) {
    operator fun invoke(config: CryptoManager.PasswordConfig = CryptoManager.PasswordConfig()): CharArray {
        return crypto.generatePassword(config)
    }
}

/**
 * Changes the master password by re-creating the vault with the new password
 * and re-inserting all existing entries and groups.
 *
 * LIMITATION: This is a destructive operation. The old vault file is deleted
 * and a new one is created. Biometric enrollment is cleared.
 */
class ChangeMasterPasswordUseCase @Inject constructor(
    private val repo: VaultRepository,
    private val crypto: CryptoManager
) {
    suspend operator fun invoke(oldPassword: String, newPassword: String): Result<Unit> {
        // Unlock with old password
        val unlockResult = repo.unlock(oldPassword)
        if (unlockResult is com.vaultzero.app.domain.model.UnlockResult.Error) {
            return Result.failure(IllegalArgumentException("Old password is incorrect"))
        }

        // Backup current data
        val entries = repo.entries.first()
        val groups = repo.groups.first()

        // Lock and clear
        repo.lock()
        repo.clearBiometricKey()

        // Delete old vault
        // (repo implementation handles this on next createVault)

        // Create new vault with new password
        val createResult = repo.createVault(newPassword)
        if (createResult is com.vaultzero.app.domain.model.UnlockResult.Error) {
            return Result.failure(IllegalStateException(createResult.message))
        }

        // Re-insert groups first (entries reference them)
        groups.forEach { repo.saveGroup(it) }
        entries.forEach { repo.saveEntry(it) }

        return Result.success(Unit)
    }
}
