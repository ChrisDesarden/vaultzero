package com.vaultzero.app.domain.usecase

import com.vaultzero.app.crypto.CryptoManager
import com.vaultzero.app.domain.model.UnlockResult
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.repository.VaultRepository
import javax.inject.Inject

class CreateVaultUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(password: String): UnlockResult = repo.createVault(password)
}

class UnlockVaultUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(password: String): UnlockResult = repo.unlock(password)
}

class LockVaultUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke() = repo.lock()
}

class IsVaultCreatedUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(): Boolean = repo.isVaultCreated()
}

class GetEntriesUseCase @Inject constructor(private val repo: VaultRepository) {
    operator fun invoke(groupId: String?) = repo.getEntries(groupId)
}

class SearchEntriesUseCase @Inject constructor(private val repo: VaultRepository) {
    operator fun invoke(query: String) = repo.searchEntries(query)
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

class GetGroupsUseCase @Inject constructor(private val repo: VaultRepository) {
    operator fun invoke() = repo.getGroups()
}

class SaveGroupUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(group: VaultGroup) = repo.saveGroup(group)
}

class DeleteGroupUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(groupId: String) = repo.deleteGroup(groupId)
}

class GeneratePasswordUseCase @Inject constructor(private val crypto: CryptoManager) {
    operator fun invoke(config: CryptoManager.PasswordConfig = CryptoManager.PasswordConfig()): CharArray =
        crypto.generatePassword(config)
}

class ChangeMasterPasswordUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(oldPassword: String, newPassword: String): UnlockResult {
        val unlockResult = repo.unlock(oldPassword)
        if (unlockResult !is UnlockResult.Success) return unlockResult
        val entries = repo.entries
        val groups = repo.groups
        repo.lock()
        val createResult = repo.createVault(newPassword)
        if (createResult !is UnlockResult.Success) return createResult
        groups.collect { it.forEach { g -> repo.saveGroup(g) } }
        entries.collect { it.forEach { e -> repo.saveEntry(e) } }
        repo.clearBiometricKey()
        repo.setBiometricEnabled(false)
        return UnlockResult.Success
    }
}
