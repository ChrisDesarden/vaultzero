package com.vaultzero.app.domain.usecase

import android.net.Uri
import com.vaultzero.app.crypto.CryptoManager
import com.vaultzero.app.domain.model.PasswordGeneratorDefaults
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.model.VaultSettings
import com.vaultzero.app.domain.repository.VaultRepository
import javax.inject.Inject

class UnlockVaultUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(password: String) = repo.unlock(password)
}

class LockVaultUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke() = repo.lock()
}

class CreateVaultUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(password: String) = repo.createVault(password)
}

class IsVaultCreatedUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke() = repo.isVaultCreated()
}

class GetEntriesUseCase @Inject constructor(private val repo: VaultRepository) {
    operator fun invoke(groupId: String? = null) = repo.entries
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
    operator fun invoke() = repo.groups
}

class SaveGroupUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(group: VaultGroup) = repo.saveGroup(group)
}

class DeleteGroupUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(groupId: String) = repo.deleteGroup(groupId)
}

class GetSettingsUseCase @Inject constructor(private val repo: VaultRepository) {
    operator fun invoke() = repo.getSettings()
}

class SaveSettingsUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(settings: VaultSettings) = repo.saveSettings(settings)
}

class GeneratePasswordUseCase @Inject constructor(
    private val crypto: CryptoManager
) {
    operator fun invoke(defaults: PasswordGeneratorDefaults = PasswordGeneratorDefaults()): String {
        val config = CryptoManager.PasswordConfig(
            length = defaults.length,
            includeUppercase = defaults.includeUppercase,
            includeLowercase = defaults.includeLowercase,
            includeNumbers = defaults.includeNumbers,
            includeSymbols = defaults.includeSymbols,
            excludeAmbiguous = defaults.excludeAmbiguous
        )
        return crypto.generatePassword(config).concatToString()
    }
}

class ExportVaultUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(uri: Uri): Boolean = repo.exportVault(uri)
}

class ImportVaultUseCase @Inject constructor(private val repo: VaultRepository) {
    suspend operator fun invoke(uri: Uri): Boolean = repo.importVault(uri)
}
