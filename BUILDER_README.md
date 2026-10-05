# VaultZero — Builder Agent (Agent 2) README

This document describes the core data layer and crypto implementation built by Agent 2.

## Build Instructions

```bash
# Sync dependencies
./gradlew dependencies

# Compile debug APK
./gradlew assembleDebug

# Run unit tests (JVM)
./gradlew testDebugUnitTest
```

## Dependencies and Versions

| Library | Version | Purpose |
|---------|---------|---------|
| SQLCipher Android | 4.6.1 | Transparent SQLite encryption |
| Room | 2.6.1 | ORM + compile-time SQL verification |
| BouncyCastle | 1.78 | PBKDF2 + AES-GCM (JVM unit tests) |
| Argon2 JVM | 1.18.0 | Argon2id KDF (JVM unit tests) |
| Hilt | 2.52 | Dependency injection |
| DataStore | 1.1.1 | Settings persistence (plaintext, non-sensitive) |
| kotlinx-coroutines | 1.9.0 | Flow + async |

**Note:** BouncyCastle and Argon2-JVM are bundled as `implementation` in `app/build.gradle.kts`.
On Android, Argon2-JVM native libs may not load on ARM devices — the `CryptoManager` gracefully
falls back to PBKDF2 if `deriveWithArgon2id` throws `UnsatisfiedLinkError`.

## Architecture

```
Presentation (Agent 3)          Domain                Data (Agent 2)
┌─────────────────┐            ┌──────────────┐     ┌─────────────────────────┐
│ UnlockScreen      │◄──UseCase──┤ VaultRepository│◄────┤ SqlCipherVaultRepository│
│ HomeScreen        │            │  (interface) │     │   - SQLCipher DB        │
│ SettingsScreen    │            └──────────────┘     │   - DataStore settings  │
│ EntryDetailScreen │                               │   - CryptoManager         │
└─────────────────┘                               └─────────────────────────┘
                                                          │
                                                          ▼
                                                    ┌──────────────┐
                                                    │ CryptoManager│
                                                    │ - Argon2id   │
                                                    │ - PBKDF2     │
                                                    │ - AES-GCM    │
                                                    └──────────────┘
```

## Security Model

1. **SQLCipher** encrypts the entire database file with a 256-bit passphrase derived from the
   user's master key via HMAC-SHA256(masterKey, "VaultZero/SQLCipher/v1").
2. **KDF salt** (16 bytes) is stored in a plaintext sidecar file (`vault.db.salt`). The salt is
   not secret — it only prevents rainbow-table attacks.
3. **Per-entry encryption**: passwords and notes are encrypted with per-entry keys derived via
   HMAC-SHA256(masterKey, "VaultZero/Entry/1.0" + entryUuid). This provides defense-in-depth
   even if SQLCipher is compromised.
4. **Master key lifetime**: the derived master key lives only in `sessionMasterKey` while the
   vault is unlocked. It is wiped (zero-filled) on `lock()`.
5. **Best-effort secure memory**: `CryptoManager.wipe()` overwrites ByteArray/CharArray contents.
   On Android, this is best-effort — JVM garbage collection and copy-on-write may leave copies
   in memory.

## KDF Defaults

| Parameter | Default | Notes |
|-----------|---------|-------|
| Algorithm | Argon2id | Preferred; falls back to PBKDF2 on Android ARM if native lib unavailable |
| Memory | 64 MB | `ARGON2_MEMORY_KB = 65536` |
| Iterations | 3 | Argon2id time cost |
| Parallelism | 4 | Argon2id lanes |
| PBKDF2 fallback | 600,000 | OWASP 2023 recommendation for SHA-512 |

## API Contract for Agent 3 (UI Layer)

### Vault Lifecycle

```kotlin
// First run
val created: UnlockResult = createVaultUseCase("master_password")

// Unlock
val unlocked: UnlockResult = unlockVaultUseCase("master_password")

// Lock
lockVaultUseCase()

// Check state
val isCreated: Boolean = isVaultCreatedUseCase()
val locked: Flow<Boolean> = observeVaultLockStateUseCase() // inverted isUnlocked
```

### Entries

```kotlin
val allEntries: Flow<List<VaultEntry>> = getEntriesUseCase(groupId = null)
val groupEntries: Flow<List<VaultEntry>> = getEntriesUseCase(groupId = "group_1")
val entry: VaultEntry? = getEntryUseCase("entry_uuid")
saveEntryUseCase(entry)
deleteEntryUseCase("entry_uuid")
val results: Flow<List<VaultEntry>> = searchEntriesUseCase("query")
```

### Groups

```kotlin
val groups: Flow<List<VaultGroup>> = getGroupsUseCase()
saveGroupUseCase(group)
deleteGroupUseCase("group_uuid")
```

### Settings (DataStore-backed)

```kotlin
val theme: Flow<AppTheme> = repository.theme
val timeout: Flow<AutoLockTimeout> = repository.autoLockTimeout
val defaults: Flow<PasswordGeneratorDefaults> = repository.passwordDefaults
val biometric: Flow<Boolean> = repository.biometricEnabled

// Mutations (suspend)
repository.setTheme(AppTheme.DARK)
repository.setAutoLockTimeout(AutoLockTimeout.FIVE_MINUTES)
repository.setPasswordDefaults(PasswordGeneratorDefaults(...))
repository.setBiometricEnabled(true)
```

### Password Generation

```kotlin
val password: CharArray = generatePasswordUseCase(
    CryptoManager.PasswordConfig(length = 20, includeSymbols = true)
)
// Remember to clear: crypto.wipe(password)
```

### Biometric Bridge

```kotlin
// Enrollment: after successful unlock, encrypt DB key with biometric key
val encryptedDbKey: ByteArray = biometricHelper.encrypt(cipher, dbKeyBytes)
repository.setBiometricKeyEncryptedDbKey(encryptedDbKey)

// Retrieval
val encKey: ByteArray? = repository.getBiometricKeyEncryptedDbKey()

// Clear (user disables biometric)
repository.clearBiometricKey()
repository.setBiometricEnabled(false)
```

## Assumptions

- `minSdk = 26` (Android 8.0) — SQLCipher 4.x native libs require API 26+.
- SQLCipher 4.6.1 is bundled with `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86` ABIs.
- The `javax.crypto` package is available on all Android API 26+ devices.
- Room schema export is disabled (`exportSchema = false`) for simplicity.
- Export/import (`.exportVault()`, `.importVault()`) are stubs for v1 — full implementation
defers to Agent 3 or a future iteration.

## File Inventory (Agent 2)

| File | Purpose |
|------|---------|
| `data/local/entity/VaultMetadataEntity.kt` | Singleton metadata row (KDF params, encrypted DB key) |
| `data/local/entity/GroupEntity.kt` | Room entity for groups/folders |
| `data/local/entity/EntryEntity.kt` | Room entity for password entries (encrypted fields) |
| `data/local/dao/VaultMetadataDao.kt` | Metadata CRUD + Flow observation |
| `data/local/dao/GroupDao.kt` | Group CRUD + Flow observation |
| `data/local/dao/EntryDao.kt` | Entry CRUD + search + Flow observation |
| `data/local/db/VaultDatabase.kt` | RoomDatabase + SQLCipher SupportFactory |
| `data/repository/SqlCipherVaultRepository.kt` | Real implementation of VaultRepository |
| `crypto/CryptoManager.kt` | Argon2id/PBKDF2 KDF, AES-GCM, password generator |
| `di/AppModule.kt` | Hilt module binding SqlCipherVaultRepository |
| `domain/usecase/UseCases.kt` | Use cases for Agent 3 |
| `domain/repository/VaultRepository.kt` | Canonical interface (DO NOT CHANGE) |
| `domain/model/VaultModels.kt` | Domain models + UnlockResult sealed class |
| `test/data/crypto/CryptoManagerTest.kt` | Unit tests for KDF, AES-GCM, password gen |

## Known Limitations

1. **Argon2id on Android ARM**: `de.mkammerer:argon2-jvm` bundles x64 JNI only. On ARM Android,
   `deriveWithArgon2id` will throw `UnsatisfiedLinkError`. The fallback to PBKDF2 is automatic
   but should be considered a temporary measure. A proper fix is to integrate an ARM-native
   Argon2 library (e.g., via NDK) or use a pure-Kotlin implementation.
2. **Secure memory**: JVM/Android does not guarantee secure memory wiping. `wipe()` is best-effort.
3. **Biometric**: The biometric bridge stores the encrypted DB key in DataStore (AES-256-GCM
   ciphertext). The actual biometric key management is handled by `BiometricHelper` (Agent 3).
4. **Export/Import**: Stubs only. A full implementation would need a header format, version
   negotiation, and streaming for large vaults.

## Commit Message

When committing Agent 2 work:
```
feat(core): VaultZero data layer and crypto manager

- Add SQLCipher-backed Room database with per-entry AES-GCM encryption
- Implement CryptoManager with Argon2id/PBKDF2 KDF and AES-256-GCM
- Add VaultRepository implementation (SqlCipherVaultRepository)
- Add domain use cases for vault lifecycle, entries, groups, settings
- Add unit tests for crypto operations
- Document API contract for Agent 3 (UI/biometrics)
```
