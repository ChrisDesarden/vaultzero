package com.vaultzero.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vaultzero.app.domain.model.AppTheme
import com.vaultzero.app.domain.model.AutoLockTimeout
import com.vaultzero.app.domain.model.PasswordGeneratorDefaults
import com.vaultzero.app.domain.model.UnlockResult
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.model.VaultSettings
import com.vaultzero.app.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory stub implementation of [VaultRepository] for UI development.
 * Agent 2 (Builder) replaces this with the real encrypted database implementation.
 */
@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val context: Context
) : VaultRepository {

    companion object {
        private const val DB_NAME = "vault.db"
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vaultzero_prefs")

        private val PREF_THEME = stringPreferencesKey("theme")
        private val PREF_AUTO_LOCK = intPreferencesKey("auto_lock_seconds")
        private val PREF_PW_LENGTH = intPreferencesKey("pw_length")
        private val PREF_PW_UPPER = booleanPreferencesKey("pw_upper")
        private val PREF_PW_LOWER = booleanPreferencesKey("pw_lower")
        private val PREF_PW_NUMBERS = booleanPreferencesKey("pw_numbers")
        private val PREF_PW_SYMBOLS = booleanPreferencesKey("pw_symbols")
        private val PREF_PW_AMBIGUOUS = booleanPreferencesKey("pw_ambiguous")
        private val PREF_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val PREF_BIOMETRIC_ENC_DB_KEY = stringPreferencesKey("biometric_enc_db_key")
    }

    private val dbFile: File get() = context.getDatabasePath(DB_NAME)

    private val _isUnlocked = MutableStateFlow(false)
    override val isUnlocked: Flow<Boolean> = _isUnlocked.asStateFlow()

    private var vaultPassword: String? = null

    private val _entries = MutableStateFlow(
        listOf(
            VaultEntry(
                id = "entry_1",
                title = "Example Email",
                username = "user@example.com",
                password = "S3cureP@ss!",
                url = "https://example.com",
                notes = "Demo entry",
                groupId = "group_1",
                createdAt = 1700000000000,
                modifiedAt = 1700000000000
            ),
            VaultEntry(
                id = "entry_2",
                title = "Wi-Fi Router",
                username = "admin",
                password = "router-password-2024",
                url = "http://192.168.1.1",
                groupId = "group_2",
                createdAt = 1700000000000,
                modifiedAt = 1700000000000
            )
        )
    )
    override val entries: Flow<List<VaultEntry>> = _entries.asStateFlow()

    override fun getEntries(groupId: String?): Flow<List<VaultEntry>> {
        return if (groupId == null) {
            _entries.asStateFlow()
        } else {
            _entries.asStateFlow().map { list ->
                list.filter { it.groupId == groupId }
            }
        }
    }

    private val _groups = MutableStateFlow(
        listOf(
            VaultGroup(id = "group_1", name = "Email", createdAt = 1700000000000, modifiedAt = 1700000000000),
            VaultGroup(id = "group_2", name = "Network", createdAt = 1700000000000, modifiedAt = 1700000000000),
            VaultGroup(id = "group_3", name = "Finance", createdAt = 1700000000000, modifiedAt = 1700000000000)
        )
    )
    override val groups: Flow<List<VaultGroup>> = _groups.asStateFlow()

    override fun getGroups(): Flow<List<VaultGroup>> = _groups.asStateFlow()

    override val theme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        prefs[PREF_THEME]?.let { AppTheme.valueOf(it) } ?: AppTheme.SYSTEM
    }

    override val autoLockTimeout: Flow<AutoLockTimeout> = context.dataStore.data.map { prefs ->
        val seconds = prefs[PREF_AUTO_LOCK] ?: 300
        AutoLockTimeout.entries.find { it.seconds == seconds } ?: AutoLockTimeout.FIVE_MINUTES
    }

    override val passwordDefaults: Flow<PasswordGeneratorDefaults> = context.dataStore.data.map { prefs ->
        PasswordGeneratorDefaults(
            length = prefs[PREF_PW_LENGTH] ?: 16,
            includeUppercase = prefs[PREF_PW_UPPER] ?: true,
            includeLowercase = prefs[PREF_PW_LOWER] ?: true,
            includeNumbers = prefs[PREF_PW_NUMBERS] ?: true,
            includeSymbols = prefs[PREF_PW_SYMBOLS] ?: true,
            excludeAmbiguous = prefs[PREF_PW_AMBIGUOUS] ?: true
        )
    }

    override val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PREF_BIOMETRIC_ENABLED] ?: false
    }

    override fun getSettings(): Flow<VaultSettings> {
        return combine(theme, autoLockTimeout, passwordDefaults, biometricEnabled) { t, to, pd, be ->
            VaultSettings(
                theme = t,
                autoLockTimeout = to,
                passwordLength = pd.length,
                includeUppercase = pd.includeUppercase,
                includeLowercase = pd.includeLowercase,
                includeDigits = pd.includeNumbers,
                includeSymbols = pd.includeSymbols,
                excludeAmbiguous = pd.excludeAmbiguous,
                clipboardClearSeconds = 30,
                biometricEnabled = be
            )
        }
    }

    override suspend fun saveSettings(settings: VaultSettings) {
        context.dataStore.edit {
            it[PREF_THEME] = settings.theme.name
            it[PREF_AUTO_LOCK] = settings.autoLockTimeout.seconds
            it[PREF_PW_LENGTH] = settings.passwordLength
            it[PREF_PW_UPPER] = settings.includeUppercase
            it[PREF_PW_LOWER] = settings.includeLowercase
            it[PREF_PW_NUMBERS] = settings.includeDigits
            it[PREF_PW_SYMBOLS] = settings.includeSymbols
            it[PREF_PW_AMBIGUOUS] = settings.excludeAmbiguous
            it[PREF_BIOMETRIC_ENABLED] = settings.biometricEnabled
        }
    }

    // ------------------------------------------------------------------
    // Vault lifecycle
    // ------------------------------------------------------------------

    override suspend fun createVault(password: String): UnlockResult {
        vaultPassword = password
        _isUnlocked.value = true
        return UnlockResult.Success
    }

    override suspend fun isVaultCreated(): Boolean {
        return vaultPassword != null
    }

    override suspend fun unlock(password: String): UnlockResult {
        return if (vaultPassword == null) {
            vaultPassword = password
            _isUnlocked.value = true
            UnlockResult.Success
        } else if (vaultPassword == password) {
            _isUnlocked.value = true
            UnlockResult.Success
        } else {
            UnlockResult.Error("Incorrect password")
        }
    }

    override suspend fun isUnlocked(): Boolean = _isUnlocked.value

    override suspend fun lock() {
        _isUnlocked.value = false
    }

    // ------------------------------------------------------------------
    // Entries
    // ------------------------------------------------------------------

    override suspend fun getEntry(entryId: String): VaultEntry? =
        _entries.value.find { it.id == entryId }

    override suspend fun saveEntry(entry: VaultEntry) {
        val current = _entries.value.toMutableList()
        val idx = current.indexOfFirst { it.id == entry.id }
        val toSave = entry.copy(modifiedAt = System.currentTimeMillis())
        if (idx >= 0) {
            current[idx] = toSave
        } else {
            current.add(toSave)
        }
        _entries.value = current
    }

    override suspend fun deleteEntry(entryId: String) {
        _entries.value = _entries.value.filter { it.id != entryId }
    }

    override fun searchEntries(query: String): Flow<List<VaultEntry>> {
        return if (query.isBlank()) {
            _entries.asStateFlow()
        } else {
            _entries.asStateFlow().map { list ->
                list.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.username.contains(query, ignoreCase = true) ||
                        it.url.contains(query, ignoreCase = true) ||
                        it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Groups
    // ------------------------------------------------------------------

    override suspend fun saveGroup(group: VaultGroup) {
        val current = _groups.value.toMutableList()
        val idx = current.indexOfFirst { it.id == group.id }
        if (idx >= 0) {
            current[idx] = group
        } else {
            current.add(group)
        }
        _groups.value = current
    }

    override suspend fun deleteGroup(groupId: String) {
        _groups.value = _groups.value.filter { it.id != groupId }
    }

    // ------------------------------------------------------------------
    // Settings (individual setters already covered above)
    // ------------------------------------------------------------------

    override suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[PREF_THEME] = theme.name }
    }

    override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        context.dataStore.edit { it[PREF_AUTO_LOCK] = timeout.seconds }
    }

    override suspend fun setPasswordDefaults(defaults: PasswordGeneratorDefaults) {
        context.dataStore.edit {
            it[PREF_PW_LENGTH] = defaults.length
            it[PREF_PW_UPPER] = defaults.includeUppercase
            it[PREF_PW_LOWER] = defaults.includeLowercase
            it[PREF_PW_NUMBERS] = defaults.includeNumbers
            it[PREF_PW_SYMBOLS] = defaults.includeSymbols
            it[PREF_PW_AMBIGUOUS] = defaults.excludeAmbiguous
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PREF_BIOMETRIC_ENABLED] = enabled }
    }

    // ------------------------------------------------------------------
    // Biometric bridge
    // ------------------------------------------------------------------

    override suspend fun setBiometricKeyEncryptedDbKey(encryptedDbKey: ByteArray) {
        context.dataStore.edit { it[PREF_BIOMETRIC_ENC_DB_KEY] = java.util.Base64.getEncoder().encodeToString(encryptedDbKey) }
    }

    override suspend fun getBiometricKeyEncryptedDbKey(): ByteArray? {
        val b64 = context.dataStore.data.first()[PREF_BIOMETRIC_ENC_DB_KEY] ?: return null
        return try {
            java.util.Base64.getDecoder().decode(b64)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clearBiometricKey() {
        context.dataStore.edit { it.remove(PREF_BIOMETRIC_ENC_DB_KEY) }
    }

    // ------------------------------------------------------------------
    // Import / Export (stubs)
    // ------------------------------------------------------------------

    override suspend fun exportVault(uri: Uri): Boolean = false

    override suspend fun importVault(uri: Uri): Boolean = false
}
