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
import androidx.room.Room
import com.vaultzero.app.crypto.CryptoManager
import com.vaultzero.app.data.local.dao.EntryDao
import com.vaultzero.app.data.local.dao.GroupDao
import com.vaultzero.app.data.local.dao.VaultMetadataDao
import com.vaultzero.app.data.local.db.VaultDatabase
import com.vaultzero.app.data.local.entity.EntryEntity
import com.vaultzero.app.data.local.entity.GroupEntity
import com.vaultzero.app.data.local.entity.VaultMetadataEntity
import com.vaultzero.app.domain.model.AppTheme
import com.vaultzero.app.domain.model.AutoLockTimeout
import com.vaultzero.app.domain.model.PasswordGeneratorDefaults
import com.vaultzero.app.domain.model.UnlockResult
import com.vaultzero.app.domain.model.VaultEntry
import com.vaultzero.app.domain.model.VaultGroup
import com.vaultzero.app.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import net.sqlcipher.database.SupportFactory
import org.bouncycastle.util.encoders.Hex
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SqlCipherVaultRepository @Inject constructor(
    private val context: Context,
    private val crypto: CryptoManager
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
    private val saltFile: File get() = File(dbFile.parent, "$DB_NAME.salt")

    private val _isUnlocked = MutableStateFlow(false)
    override val isUnlocked: Flow<Boolean> = _isUnlocked.asStateFlow()

    private var sessionMasterKey: ByteArray? = null
    private var database: VaultDatabase? = null
    private val metadataDao: VaultMetadataDao? get() = database?.vaultMetadataDao()
    private val entryDao: EntryDao? get() = database?.entryDao()
    private val groupDao: GroupDao? get() = database?.groupDao()

    override suspend fun createVault(password: String): UnlockResult {
        if (dbFile.exists()) dbFile.delete()
        if (saltFile.exists()) saltFile.delete()

        val salt = crypto.randomBytes(16)
        val masterKey = crypto.deriveMasterKey(
            password.toCharArray(),
            salt = salt,
            useArgon2 = true
        )

        return try {
            writeSaltFile(salt)
            openDatabaseWithMasterKey(masterKey)
            val metadata = VaultMetadataEntity(
                kdfType = 1,
                kdfSalt = salt,
                kdfParams = """{"memoryKB":${CryptoManager.ARGON2_MEMORY_KB},"iterations":${CryptoManager.ARGON2_ITERATIONS},"parallelism":${CryptoManager.ARGON2_PARALLELISM}}""".toByteArray(),
                encryptedDbKey = byteArrayOf()
            )
            metadataDao?.insert(metadata)
            sessionMasterKey = masterKey.copyOf()
            _isUnlocked.value = true
            UnlockResult.Success
        } catch (e: Exception) {
            crypto.wipe(masterKey)
            dbFile.delete()
            saltFile.delete()
            UnlockResult.Error(e.message ?: "Failed to create vault")
        }
    }

    override suspend fun unlock(password: String): UnlockResult {
        if (_isUnlocked.value) return UnlockResult.Success
        if (!dbFile.exists()) return UnlockResult.Error("Vault not found. Create one first.")
        if (!saltFile.exists()) return UnlockResult.Error("Vault salt file missing.")

        val salt = saltFile.readBytes().copyOf(16)
        return try {
            val masterKey = crypto.deriveMasterKey(
                password.toCharArray(),
                salt = salt,
                useArgon2 = true
            )
            openDatabaseWithMasterKey(masterKey)
            sessionMasterKey = masterKey.copyOf()
            _isUnlocked.value = true
            UnlockResult.Success
        } catch (e: Exception) {
            crypto.wipe(sessionMasterKey)
            sessionMasterKey = null
            UnlockResult.Error("Wrong password or corrupted vault")
        }
    }

    override suspend fun lock() {
        crypto.wipe(sessionMasterKey)
        sessionMasterKey = null
        database?.close()
        database = null
        _isUnlocked.value = false
    }

    override suspend fun isVaultCreated(): Boolean {
        return dbFile.exists() && saltFile.exists()
    }

    private fun openDatabaseWithMasterKey(masterKey: ByteArray) {
        database?.close()
        val passphrase = crypto.hmacSha256(masterKey, "VaultZero/SQLCipher/v1".toByteArray())
        val factory = SupportFactory(passphrase)
        database = Room.databaseBuilder(context, VaultDatabase::class.java, DB_NAME)
            .openHelperFactory(factory)
            .build()
    }

    private fun writeSaltFile(salt: ByteArray) {
        saltFile.writeBytes(salt)
    }

    override val entries: Flow<List<VaultEntry>> = _isUnlocked
        .flatMapLatest { unlocked ->
            if (unlocked && entryDao != null) {
                entryDao!!.observeAll().map { list -> list.map { entity -> decryptEntry(entity) } }
            } else {
                flowOf(emptyList())
            }
        }

    override suspend fun getEntry(entryId: String): VaultEntry? {
        val entity = entryDao?.getByUuid(entryId) ?: return null
        return decryptEntry(entity)
    }

    override suspend fun saveEntry(entry: VaultEntry) {
        val key = requireMasterKey()
        val entity = encryptEntry(entry, key)
        entryDao?.insert(entity)
    }

    override suspend fun deleteEntry(entryId: String) {

    override fun getEntries(groupId: String?): Flow<List<VaultEntry>> {
        return if (groupId == null) entries else entries.map { it.filter { e -> e.groupId == groupId } }
    }

    override fun getGroups(): Flow<List<VaultGroup>> = groups

    override fun getSettings(): Flow<VaultSettings> = combine(theme, autoLockTimeout, passwordDefaults, biometricEnabled) { t, a, p, b ->
        VaultSettings(theme = t, autoLockTimeout = a, passwordLength = p.length, includeUppercase = p.includeUppercase, includeLowercase = p.includeLowercase, includeDigits = p.includeNumbers, includeSymbols = p.includeSymbols, excludeAmbiguous = p.excludeAmbiguous, clipboardClearSeconds = 30, biometricEnabled = b)
    }

    override suspend fun saveSettings(settings: VaultSettings) {
        setTheme(settings.theme)
        setAutoLockTimeout(settings.autoLockTimeout)
        setPasswordDefaults(PasswordGeneratorDefaults(length = settings.passwordLength, includeUppercase = settings.includeUppercase, includeLowercase = settings.includeLowercase, includeNumbers = settings.includeDigits, includeSymbols = settings.includeSymbols, excludeAmbiguous = settings.excludeAmbiguous))
        setBiometricEnabled(settings.biometricEnabled)
    }
        entryDao?.deleteByUuid(entryId)
    }

    override fun searchEntries(query: String): Flow<List<VaultEntry>> = _isUnlocked
        .flatMapLatest { unlocked ->
            if (unlocked && entryDao != null) {
                entryDao!!.search(query).let { list -> flowOf(list.map { decryptEntry(it) }) }
            } else {
                flowOf(emptyList())
            }
        }

    override val groups: Flow<List<VaultGroup>> = _isUnlocked
        .flatMapLatest { unlocked ->
            if (unlocked && groupDao != null) {
                groupDao!!.observeAll().map { list -> list.map { it.toDomain() } }
            } else {
                flowOf(emptyList())
            }
        }

    override suspend fun saveGroup(group: VaultGroup) {
        val entity = group.toEntity()
        groupDao?.insert(entity)
    }

    override suspend fun deleteGroup(groupId: String) {
        groupDao?.deleteByUuid(groupId)
    }

    private fun requireMasterKey(): ByteArray =
        sessionMasterKey ?: throw IllegalStateException("Vault is locked")

    private fun decryptEntry(entity: EntryEntity): VaultEntry {
        val key = requireMasterKey()
        val entryKey = crypto.deriveEntryKey(key, entity.uuid)
        return try {
            val passwordChars = crypto.decryptPassword(entryKey, entity.passwordCipher)
            val notesChars = entity.notesCipher?.let { crypto.decryptPassword(entryKey, it) }
            VaultEntry(
                id = entity.uuid,
                title = entity.title,
                username = entity.username,
                password = passwordChars.concatToString(),
                url = entity.url,
                notes = notesChars?.concatToString() ?: "",
                tags = parseTags(entity.tags),
                groupId = entity.groupUuid,
                favorite = entity.favorite,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        } finally {
            crypto.wipe(entryKey)
        }
    }

    private fun encryptEntry(entry: VaultEntry, masterKey: ByteArray): EntryEntity {
        val entryKey = crypto.deriveEntryKey(masterKey, entry.id)
        return try {
            val passwordCipher = crypto.encryptPassword(entryKey, entry.password.toCharArray())
            val notesCipher = entry.notes.takeIf { it.isNotBlank() }?.let {
                crypto.encryptPassword(entryKey, it.toCharArray())
            }
            EntryEntity(
                uuid = entry.id.takeIf { it.isNotBlank() } ?: crypto.randomUuid(),
                title = entry.title,
                username = entry.username,
                passwordCipher = passwordCipher,
                url = entry.url,
                notesCipher = notesCipher,
                tags = serializeTags(entry.tags),
                groupUuid = entry.groupId,
                favorite = entry.favorite,
                createdAt = entry.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        } finally {
            crypto.wipe(entryKey)
        }
    }

    private fun parseTags(tagsJson: String): List<String> {
        if (tagsJson.isBlank()) return emptyList()
        return try { org.json.JSONArray(tagsJson).let { arr -> (0 until arr.length()).map { arr.getString(it) } } }
        catch (_: Exception) { emptyList() }
    }

    private fun serializeTags(tags: List<String>): String {
        return org.json.JSONArray(tags).toString()
    }

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
            excludeAmbiguous = prefs[PREF_PW_AMBIGUOUS] ?: false
        )
    }

    override val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PREF_BIOMETRIC_ENABLED] ?: false
    }

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

    override suspend fun exportVault(uri: Uri): Boolean {
        return false
    }

    override suspend fun importVault(uri: Uri): Boolean {
        return false
    }

    override suspend fun setBiometricKeyEncryptedDbKey(encryptedDbKey: ByteArray) {
        val hex = Hex.toHexString(encryptedDbKey)
        context.dataStore.edit { it[PREF_BIOMETRIC_ENC_DB_KEY] = hex }
    }

    override suspend fun getBiometricKeyEncryptedDbKey(): ByteArray? {
        val hex = context.dataStore.data.first()[PREF_BIOMETRIC_ENC_DB_KEY] ?: return null
        return Hex.decode(hex)
    }

    override suspend fun clearBiometricKey() {
        context.dataStore.edit { it.remove(PREF_BIOMETRIC_ENC_DB_KEY) }
    }

    private fun GroupEntity.toDomain() = VaultGroup(
        id = uuid,
        name = name,
        parentId = parentUuid,
        icon = icon,
        sortOrder = sortOrder,
        createdAt = createdAt
    )

    private fun VaultGroup.toEntity() = GroupEntity(
        uuid = id.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
        name = name,
        parentUuid = parentId,
        icon = icon,
        sortOrder = sortOrder,
        createdAt = createdAt
    )
}
