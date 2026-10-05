package com.vaultzero.app.data.repository

import android.util.Log

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
import com.vaultzero.app.domain.model.VaultSettings
import com.vaultzero.app.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.bouncycastle.util.encoders.Hex
import org.json.JSONArray
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SQLCipher-backed implementation of [VaultRepository].
 *
 * Security design:
 *  - The master password never persists.
 *  - A random salt is stored in a sidecar file next to the DB.
 *  - PBKDF2-HMAC-SHA512 derives a 256-bit master key from password + salt.
 *  - The SQLCipher passphrase is HMAC-SHA256(masterKey, "VaultZero/SQLCipher/v1").
 *  - Each entry's password and notes are encrypted with a per-entry key derived from
 *    the master key + entry UUID (AES-256-GCM).
 */
@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val biometricCrypto: com.vaultzero.app.presentation.biometric.BiometricCrypto,
    private val context: Context,
    private val crypto: CryptoManager
) : VaultRepository {

    companion object {
        private const val DB_NAME = "vault.db"
        private const val KDF_TYPE_PBKDF2 = 2
        private const val SQLCIPHER_AAD = "VaultZero/SQLCipher/v1"
        private val PBKDF2_PARAMS_JSON =
            """{"iterations":${CryptoManager.PBKDF2_ITERATIONS}}"""

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
        val masterKey = crypto.deriveMasterKey(password.toCharArray(), salt)

        return try {
            writeSaltFile(salt)
            openDatabaseWithMasterKey(masterKey)
            val metadata = VaultMetadataEntity(
                kdfType = KDF_TYPE_PBKDF2,
                kdfSalt = salt,
                kdfParams = PBKDF2_PARAMS_JSON.toByteArray(),
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
        val masterKey = try {
            crypto.deriveMasterKey(password.toCharArray(), salt)
        } catch (e: Exception) {
            return UnlockResult.Error("Key derivation failed")
        }

        return try {
            openDatabaseWithMasterKey(masterKey)
            sessionMasterKey = masterKey.copyOf()
            _isUnlocked.value = true
            UnlockResult.Success
        } catch (e: Exception) {
            crypto.wipe(masterKey)
            UnlockResult.Error("Wrong password or corrupted vault")
        }
    }

    override suspend fun unlockWithBiometric(): UnlockResult {
        if (_isUnlocked.value) return UnlockResult.Success
        if (!dbFile.exists()) return UnlockResult.Error("Vault not found. Create one first.")
        if (!saltFile.exists()) return UnlockResult.Error("Vault salt file missing.")

        val encrypted = getBiometricKeyEncryptedDbKey() ?: return UnlockResult.Error("Biometric key not enrolled")
        return try {
            val iv = encrypted.copyOfRange(0, 12)
            val cipher = biometricCrypto.getDecryptCipher(iv)
            val masterKey = biometricCrypto.decrypt(encrypted.copyOfRange(12, encrypted.size), cipher)
            openDatabaseWithMasterKey(masterKey)
            sessionMasterKey = masterKey.copyOf()
            _isUnlocked.value = true
            UnlockResult.Success
        } catch (e: Exception) {
            UnlockResult.Error("Biometric unlock failed: ${e.message}")
        }
    }

    override suspend fun lock() {
        crypto.wipe(sessionMasterKey)
        sessionMasterKey = null
        database?.close()
        database = null
        _isUnlocked.value = false
    }

    override suspend fun isUnlocked(): Boolean = _isUnlocked.value

    override suspend fun isVaultCreated(): Boolean {
        return dbFile.exists() && saltFile.exists()
    }

    private fun openDatabaseWithMasterKey(masterKey: ByteArray) {
        database?.close()
        val passphrase = crypto.hmacSha256(masterKey, SQLCIPHER_AAD.toByteArray())
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(passphrase)
        database = Room.databaseBuilder(context, VaultDatabase::class.java, DB_NAME)
            .openHelperFactory(factory)
            .build()
    }

    private fun writeSaltFile(salt: ByteArray) {
        saltFile.writeBytes(salt)
    }

    // ------------------------------------------------------------------
    // Entries
    // ------------------------------------------------------------------

    override val entries: Flow<List<VaultEntry>> = _isUnlocked
        .flatMapLatest { unlocked ->
            if (unlocked && entryDao != null) {
                entryDao!!.observeAll().map { list -> list.map { decryptEntry(it) } }
            } else {
                flowOf(emptyList())
            }
        }

    override fun getEntries(groupId: String?): Flow<List<VaultEntry>> {
        val source = if (groupId == null || entryDao == null) {
            entries
        } else {
            _isUnlocked.flatMapLatest { unlocked ->
                if (unlocked) {
                    entryDao!!.observeByGroup(groupId).map { list -> list.map { decryptEntry(it) } }
                } else {
                    flowOf(emptyList())
                }
            }
        }
        return source
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
        entryDao?.deleteByUuid(entryId)
    }

    override fun searchEntries(query: String): Flow<List<VaultEntry>> = flow {
        if (!_isUnlocked.value || entryDao == null) {
            emit(emptyList())
            return@flow
        }
        val results = entryDao!!.search(query).map { decryptEntry(it) }
        emit(results)
    }

    // ------------------------------------------------------------------
    // Groups
    // ------------------------------------------------------------------

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
        entryDao?.deleteByGroup(groupId)
        groupDao?.deleteByUuid(groupId)
    }

    // ------------------------------------------------------------------
    // Settings (granular)
    // ------------------------------------------------------------------

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

    override fun getSettings(): Flow<VaultSettings> = combine(
        theme,
        autoLockTimeout,
        passwordDefaults,
        biometricEnabled
    ) { t, a, p, b ->
        VaultSettings(
            theme = t,
            autoLockTimeout = a,
            passwordLength = p.length,
            includeUppercase = p.includeUppercase,
            includeLowercase = p.includeLowercase,
            includeDigits = p.includeNumbers,
            includeSymbols = p.includeSymbols,
            excludeAmbiguous = p.excludeAmbiguous,
            clipboardClearSeconds = 30,
            biometricEnabled = b
        )
    }

    override suspend fun saveSettings(settings: VaultSettings) {
        setTheme(settings.theme)
        setAutoLockTimeout(settings.autoLockTimeout)
        setPasswordDefaults(
            PasswordGeneratorDefaults(
                length = settings.passwordLength,
                includeUppercase = settings.includeUppercase,
                includeLowercase = settings.includeLowercase,
                includeNumbers = settings.includeDigits,
                includeSymbols = settings.includeSymbols,
                excludeAmbiguous = settings.excludeAmbiguous
            )
        )
        setBiometricEnabled(settings.biometricEnabled)
    }

    // ------------------------------------------------------------------
    // Biometric bridge
    // ------------------------------------------------------------------

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

    override suspend fun enrollBiometric(): Boolean {
        val key = sessionMasterKey ?: run {
            Log.d("VaultRepository", "enrollBiometric: sessionMasterKey is null")
            return false
        }
        return try {
            Log.d("VaultRepository", "enrollBiometric: creating cipher")
            val cipher = biometricCrypto.getEncryptCipher()
            Log.d("VaultRepository", "enrollBiometric: encrypting key")
            val encrypted = biometricCrypto.encrypt(key, cipher)
            Log.d("VaultRepository", "enrollBiometric: saving encrypted key")
            setBiometricKeyEncryptedDbKey(encrypted)
            Log.d("VaultRepository", "enrollBiometric: enabling setting")
            setBiometricEnabled(true)
            Log.d("VaultRepository", "enrollBiometric: success")
            true
        } catch (e: Exception) {
            Log.e("VaultRepository", "enrollBiometric failed", e)
            false
        }
    }

    override suspend fun disableBiometric() {
        clearBiometricKey()
        setBiometricEnabled(false)
    }

    // ------------------------------------------------------------------
    // Import / Export (encrypted CSV)
    // ------------------------------------------------------------------

    override suspend fun exportVault(uri: Uri, password: String): Boolean {
        return try {
            val entities = entryDao?.getAll() ?: return false
            val groups = groupDao?.getAll() ?: emptyList()
            val groupNames = groups.associate { it.uuid to it.name }
            val entries = entities.map { decryptEntry(it) }
            val plaintext = CsvHelper.buildCsv(entries, groupNames)
            val passChars = password.toCharArray()
            val encrypted = try {
                crypto.encryptExport(plaintext.toByteArray(StandardCharsets.UTF_8), passChars)
            } finally {
                crypto.wipe(passChars)
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(encrypted)
                out.flush()
            } ?: return false
            true
        } catch (e: Exception) {
            Log.e("VaultRepository", "exportVault failed", e)
            false
        }
    }

    override suspend fun importVault(uri: Uri, password: String): Boolean {
        return try {
            val encrypted = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return false
            val passChars = password.toCharArray()
            val plaintext = try {
                crypto.decryptExport(encrypted, passChars)
            } finally {
                crypto.wipe(passChars)
            }
            val csv = String(plaintext, StandardCharsets.UTF_8)
            val imported = CsvHelper.parseCsv(csv)
            if (imported.isEmpty()) return false

            val groupNameToId = mutableMapOf<String, String>()
            (groupDao?.getAll() ?: emptyList()).forEach { groupNameToId[it.name] = it.uuid }

            imported.forEach { row ->
                val groupName = row["group"] ?: ""
                val groupId = if (groupName.isBlank()) null else {
                    groupNameToId.getOrPut(groupName) {
                        val id = crypto.randomUuid()
                        val entity = GroupEntity(
                            uuid = id,
                            name = groupName,
                            parentUuid = null,
                            icon = null,
                            sortOrder = 0,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        groupDao?.insert(entity)
                        id
                    }
                }
                val entry = VaultEntry(
                    id = crypto.randomUuid(),
                    title = row["title"] ?: "",
                    username = row["username"] ?: "",
                    password = row["password"] ?: "",
                    url = row["url"] ?: "",
                    notes = row["notes"] ?: "",
                    tags = CsvHelper.parseTagString(row["tags"] ?: ""),
                    groupId = groupId,
                    favorite = row["favorite"].equals("true", ignoreCase = true)
                )
                saveEntry(entry)
            }
            true
        } catch (e: Exception) {
            Log.e("VaultRepository", "importVault failed", e)
            false
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------


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
                modifiedAt = entity.updatedAt
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
                updatedAt = entry.modifiedAt
            )
        } finally {
            crypto.wipe(entryKey)
        }
    }

    private fun parseTags(tagsJson: String): List<String> {
        if (tagsJson.isBlank()) return emptyList()
        return try {
            JSONArray(tagsJson).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeTags(tags: List<String>): String = JSONArray(tags).toString()

    private fun GroupEntity.toDomain() = VaultGroup(
        id = uuid,
        name = name,
        parentId = parentUuid,
        icon = icon,
        createdAt = createdAt,
        modifiedAt = updatedAt
    )

    private fun VaultGroup.toEntity() = GroupEntity(
        uuid = id.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
        name = name,
        parentUuid = parentId,
        icon = icon,
        sortOrder = 0,
        createdAt = createdAt,
        updatedAt = modifiedAt
    )

}
