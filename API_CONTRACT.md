# VaultZero — API Contract

> Public interfaces that the **Builder** and **Finisher** agents must implement. Kotlin-first, with Java-compatible signatures where noted. No UI code.

## 1. Package Structure
```
com.vaultzero.core
├── crypto/
│   ├── CryptoEngine.kt
│   ├── KeyDerivation.kt
│   ├── BiometricCrypto.kt
│   └── PasswordGenerator.kt
├── data/
│   ├── VaultDatabase.kt
│   ├── VaultRepository.kt
│   ├── SessionManager.kt
│   └── model/
│       ├── VaultMeta.kt
│       ├── Entry.kt
│       ├── Folder.kt
│       ├── DecryptedEntry.kt
│       └── AuditLog.kt
├── storage/
│   ├── VaultStorage.kt
│   ├── BackupManager.kt
│   └── EncryptedPrefs.kt
└── security/
    ├── AutoLockManager.kt
    ├── ClipboardManager.kt
    └── SecureMemory.kt
```

## 2. Core Crypto Interfaces

### 2.1 CryptoEngine
```kotlin
package com.vaultzero.core.crypto

interface CryptoEngine {
    /**
     * Derive the master key from password + optional keyfile.
     *
     * @param password   User master password. Must be wiped after use.
     * @param keyfile    Optional keyfile bytes. Null if not used.
     * @param salt       KDF salt (16 bytes).
     * @param kdfType    1=Argon2id, 2=PBKDF2.
     * @param kdfParams  JSON string with KDF-specific params.
     * @return 32-byte master key. Caller must secureZero() when done.
     */
    fun deriveMasterKey(
        password: CharArray,
        keyfile: ByteArray?,
        salt: ByteArray,
        kdfType: Int,
        kdfParams: String
    ): ByteArray

    /**
     * Derive a per-entry key from masterKey + entry UUID.
     *
     * @param masterKey The session master key.
     * @param entryUuid The entry's UUID.
     * @return 32-byte entry key. Caller must secureZero() when done.
     */
    fun deriveEntryKey(masterKey: ByteArray, entryUuid: UUID): ByteArray

    /**
     * Encrypt plaintext with AES-256-GCM.
     *
     * @param plaintext The data to encrypt.
     * @param key       32-byte key.
     * @return Pair of (ciphertext + tag, nonce).
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray): Pair<ByteArray, ByteArray>

    /**
     * Decrypt AES-256-GCM ciphertext.
     *
     * @param ciphertext Ciphertext with 16-byte tag appended.
     * @param key       32-byte key.
     * @param nonce     12-byte nonce.
     * @return Original plaintext.
     * @throws SecurityException on authentication failure.
     */
    fun decrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray

    /**
     * Compute SHA-256.
     */
    fun sha256(data: ByteArray): ByteArray

    /**
     * Compute HKDF-SHA256.
     */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: String): ByteArray
}
```

### 2.2 KeyDerivation (Implementation Detail)
```kotlin
package com.vaultzero.core.crypto

interface KeyDerivation {
    /**
     * Argon2id key derivation.
     *
     * @param password  UTF-8 bytes of the password.
     * @param salt      16-byte random salt.
     * @param memoryKB  Memory cost in KB (e.g., 65536 for 64 MB).
     * @param iterations Number of iterations.
     * @param parallelism Degree of parallelism.
     * @param keyLength  Desired key length in bytes.
     * @return Derived key.
     */
    fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        memoryKB: Int,
        iterations: Int,
        parallelism: Int,
        keyLength: Int
    ): ByteArray

    /**
     * PBKDF2-HMAC-SHA256 fallback.
     */
    fun pbkdf2(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int
    ): ByteArray
}
```

### 2.3 BiometricCrypto
```kotlin
package com.vaultzero.core.crypto

import androidx.biometric.BiometricPrompt

interface BiometricCrypto {
    /**
     * Check if biometric hardware is available and enrolled.
     */
    fun isBiometricAvailable(): BiometricAvailability

    /**
     * Generate a Keystore key and encrypt the master key for biometric storage.
     *
     * @param masterKey   The derived master key (32 bytes).
     * @param callback    Called with Result<Unit> on completion.
     */
    fun enrollBiometric(
        masterKey: ByteArray,
        callback: (Result<Unit>) -> Unit
    )

    /**
     * Decrypt the master key using biometric authentication.
     *
     * @param prompt  The BiometricPrompt instance (prepared by UI layer).
     * @param cryptoObject The CryptoObject wrapping the decrypt cipher.
     * @param callback Called with Result<ByteArray> (the master key) or failure.
     */
    fun decryptWithBiometric(
        prompt: BiometricPrompt,
        cryptoObject: BiometricPrompt.CryptoObject,
        callback: (Result<ByteArray>) -> Unit
    )

    /**
     * Delete the Keystore key and wipe encrypted master key from prefs.
     */
    fun clearBiometricEnrollment()
}

enum class BiometricAvailability {
    AVAILABLE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED,
    SECURITY_UPDATE_REQUIRED
}
```

### 2.4 PasswordGenerator
```kotlin
package com.vaultzero.core.crypto

interface PasswordGenerator {
    /**
     * Generate a random password.
     *
     * @param length            Character count (8–64).
     * @param includeUpper      Include A–Z.
     * @param includeLower      Include a–z.
     * @param includeDigits     Include 0–9.
     * @param includeSymbols    Include special chars.
     * @param excludeAmbiguous  Exclude 0, O, 1, l, I.
     * @return Generated password string.
     */
    fun generate(
        length: Int = 16,
        includeUpper: Boolean = true,
        includeLower: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true,
        excludeAmbiguous: Boolean = true
    ): String
}
```

## 3. Data Layer Interfaces

### 3.1 VaultDatabase (Room Database)
```kotlin
package com.vaultzero.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [VaultMeta::class, Entry::class, Folder::class, EntryFolder::class, AuditLog::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultMetaDao(): VaultMetaDao
    abstract fun entryDao(): EntryDao
    abstract fun folderDao(): FolderDao
    abstract fun entryFolderDao(): EntryFolderDao
    abstract fun auditLogDao(): AuditLogDao
}
```

### 3.2 VaultRepository
```kotlin
package com.vaultzero.core.data

import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    // --- Entries ---
    fun observeEntries(): Flow<List<Entry>>
    suspend fun getEntry(uuid: UUID): Entry?
    suspend fun getDecryptedEntry(uuid: UUID): DecryptedEntry?
    suspend fun saveEntry(entry: DecryptedEntry)
    suspend fun deleteEntry(uuid: UUID)
    suspend fun searchEntries(query: String): List<Entry>
    suspend fun searchByTag(tag: String): List<Entry>
    suspend fun getEntryCount(): Int

    // --- Folders ---
    fun observeFolders(): Flow<List<Folder>>
    suspend fun createFolder(name: String, parentUuid: UUID? = null): Folder
    suspend fun deleteFolder(uuid: UUID)
    suspend fun getEntriesInFolder(folderUuid: UUID): List<Entry>

    // --- Audit ---
    suspend fun logAction(action: String, entityUuid: UUID? = null, detailJson: String? = null)
    suspend fun getRecentLogs(limit: Int = 100): List<AuditLog>
    suspend fun pruneLogsOlderThan(cutoffMillis: Long)
}
```

### 3.3 DecryptedEntry (Domain Model)
```kotlin
package com.vaultzero.core.data.model

import java.util.UUID

/**
 * Plaintext representation of an entry. Only exists in memory during a session.
 * Never serialized to disk without re-encryption.
 */
data class DecryptedEntry(
    val uuid: UUID = UUID.randomUUID(),
    val title: String,
    val username: String? = null,
    val password: String,       // plaintext — wiped after display/copy
    val url: String? = null,
    val notes: String? = null,    // plaintext
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false
)
```

### 3.4 SessionManager
```kotlin
package com.vaultzero.core.data

import kotlinx.coroutines.flow.StateFlow

interface SessionManager {
    /**
     * Current session state.
     */
    val state: StateFlow<SessionState>

    /**
     * Unlock the vault with master password.
     *
     * @param password  User master password.
     * @param keyfile   Optional keyfile bytes.
     * @return Result<Unit>. On success, session enters UNLOCKED state.
     */
    suspend fun unlock(password: CharArray, keyfile: ByteArray? = null): Result<Unit>

    /**
     * Unlock via biometric (delegates to BiometricCrypto).
     */
    suspend fun unlockWithBiometric(): Result<Unit>

    /**
     * Lock the vault immediately. Wipes master key.
     */
    fun lock()

    /**
     * Check if biometric unlock is enrolled.
     */
    fun isBiometricEnrolled(): Boolean

    /**
     * Enroll biometric after first manual unlock.
     */
    suspend fun enrollBiometric(): Result<Unit>
}

sealed class SessionState {
    object Locked : SessionState()
    data class Unlocked(val masterKeyAvailable: Boolean) : SessionState()
    data class Error(val message: String) : SessionState()
}
```

## 4. Storage Interfaces

### 4.1 VaultStorage
```kotlin
package com.vaultzero.core.storage

interface VaultStorage {
    /**
     * Absolute path to the vault database file.
     */
    val vaultPath: java.io.File

    /**
     * Read the raw vault header (first 64 bytes).
     */
    suspend fun readHeader(): ByteArray

    /**
     * Write a new vault file (create or overwrite).
     */
    suspend fun writeVault(header: ByteArray, payload: ByteArray)

    /**
     * Check if a vault exists.
     */
    fun vaultExists(): Boolean

    /**
     * Securely delete the vault (overwrite + delete).
     */
    suspend fun destroyVault()
}
```

### 4.2 BackupManager
```kotlin
package com.vaultzero.core.storage

interface BackupManager {
    /**
     * Export current vault to a .vzb file via Storage Access Framework.
     *
     * @param uri Target URI from SAF document picker.
     * @return Result<Unit>.
     */
    suspend fun exportVault(uri: android.net.Uri): Result<Unit>

    /**
     * Import a .vzb file, replacing current vault.
     *
     * @param uri Source URI from SAF document picker.
     * @param password Master password for the backup.
     * @param keyfile  Optional keyfile bytes for the backup.
     * @return Result<Unit>.
     */
    suspend fun importVault(
        uri: android.net.Uri,
        password: CharArray,
        keyfile: ByteArray? = null
    ): Result<Unit>

    /**
     * Create a local auto-backup (rotated, max 3).
     */
    suspend fun createAutoBackup(): Result<java.io.File>

    /**
     * List available local backups.
     */
    fun listBackups(): List<java.io.File>
}
```

### 4.3 EncryptedPrefs
```kotlin
package com.vaultzero.core.storage

interface EncryptedPrefs {
    /**
     * Store a non-sensitive string value.
     */
    fun putString(key: String, value: String)

    fun getString(key: String, default: String? = null): String?

    /**
     * Store a long value.
     */
    fun putLong(key: String, value: Long)
    fun getLong(key: String, default: Long = 0): Long

    /**
     * Remove a key.
     */
    fun remove(key: String)

    /**
     * Wipe all preferences (destructive).
     */
    fun clear()
}
```

## 5. Security Interfaces

### 5.1 AutoLockManager
```kotlin
package com.vaultzero.core.security

interface AutoLockManager {
    /**
     * Start the auto-lock timer.
     */
    fun startTimer(timeoutMillis: Long)

    /**
     * Reset the timer (user interacted with app).
     */
    fun resetTimer()

    /**
     * Cancel the timer (app going to foreground).
     */
    fun cancelTimer()

    /**
     * Current timeout setting.
     */
    var timeoutMillis: Long
}
```

### 5.2 ClipboardManager (VaultZero wrapper)
```kotlin
package com.vaultzero.core.security

interface SecureClipboard {
    /**
     * Copy text to system clipboard and schedule clear.
     *
     * @param text        The text to copy.
     * @param timeoutMs   Auto-clear timeout (default 30s).
     */
    fun copy(text: String, timeoutMs: Long = 30_000)

    /**
     * Immediately clear the clipboard.
     */
    fun clear()
}
```

### 5.3 SecureMemory
```kotlin
package com.vaultzero.core.security

interface SecureMemory {
    /**
     * Allocate a direct ByteBuffer for sensitive data.
     */
    fun allocateDirect(size: Int): java.nio.ByteBuffer

    /**
     * Securely zero a byte array.
     */
    fun secureZero(bytes: ByteArray)

    /**
     * Securely zero a char array.
     */
    fun secureZero(chars: CharArray)
}
```

## 6. Lifecycle Events (Callbacks)

The Builder must wire these in `Application` or `ActivityLifecycleCallbacks`:

```kotlin
package com.vaultzero.core.security

interface VaultLifecycleCallbacks {
    /**
     * Called when any activity enters onResume().
     * Resets auto-lock timer if session is unlocked.
     */
    fun onAppForeground()

    /**
     * Called when all activities have stopped (app backgrounded).
     * Starts auto-lock timer.
     */
    fun onAppBackground()

    /**
     * Called on ACTION_SCREEN_OFF.
     * Immediately locks if configured (default: true).
     */
    fun onScreenOff()
}
```

## 7. Implementation Notes for Builder

### 7.1 Threading
- All `suspend` functions in `VaultRepository` and `SessionManager` must run on `Dispatchers.IO`.
- `StateFlow` emissions from `SessionManager` must happen on `Dispatchers.Main`.
- Biometric callbacks run on the main thread; offload crypto to `Dispatchers.IO`.

### 7.2 Dependency Injection
- Use constructor injection (Hilt or manual factory).
- `CryptoEngine` and `SessionManager` should be singletons.
- `VaultDatabase` is created per-session (after unlock) and closed on lock.

### 7.3 Error Handling
- All crypto failures throw `SecurityException` with descriptive messages.
- Repository layer wraps SQLCipher/SQLite exceptions in `Result.failure()`.
- UI layer never handles raw exceptions — only `Result` types.

### 7.4 Builder Checklist
- [ ] Implement `CryptoEngine` with Tink + BouncyCastle.
- [ ] Implement `KeyDerivation` with Argon2id JNI + PBKDF2 fallback.
- [ ] Implement `BiometricCrypto` with Keystore `setUserAuthenticationRequired(true)`.
- [ ] Implement `VaultDatabase` with SQLCipher `SupportFactory`.
- [ ] Implement `VaultRepository` with lazy per-entry decryption.
- [ ] Implement `SessionManager` with `StateFlow<SessionState>`.
- [ ] Implement `BackupManager` with SAF `.vzb` read/write.
- [ ] Implement `AutoLockManager` with `Handler`/`Coroutine` timeout.
- [ ] Implement `SecureClipboard` with auto-clear.
- [ ] Implement `VaultLifecycleCallbacks` wired to `Application`.
- [ ] Add `SecureMemory` with `Arrays.fill()` + optional JNI `memset`.
- [ ] Add `EncryptedPrefs` with `EncryptedSharedPreferences`.
- [ ] Unit tests for all crypto operations with test vectors.
- [ ] Integration test: create vault → add entry → lock → unlock → read entry → export → import → verify.

## 8. References
- ARCHITECTURE.md — threat model, encryption scheme, biometric flow.
- CRYPTO_SPEC.md — exact algorithms, constants, libraries.
- DATA_MODEL.md — Room entities, DAOs, SQLCipher schema.

---
*Version: 1.0 | VaultZero Codename*
