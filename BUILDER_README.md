# VaultZero — Builder README (Agent 2 Deliverable)

> **For:** Agent 3 (UI / Biometrics Finisher)  
> **Scope:** Core Kotlin data layer, crypto, and repository. No UI, no biometric prompt wiring.  
> **Commit:** `feat(core): VaultZero data layer and crypto manager`

---

## 1. What Was Built

### 1.1 Android Project Skeleton
- Gradle Kotlin DSL (`settings.gradle.kts`, root `build.gradle.kts`, `app/build.gradle.kts`)
- Version catalog in `gradle/libs.versions.toml`
- `minSdk = 26`, `targetSdk = 35`, Kotlin `2.0.20`, Java 17
- Compose enabled (for Agent 3), but NO Compose screens in this layer
- SQLCipher native libs preserved uncompressed (`useLegacyPackaging = true`)

### 1.2 Dependencies Added
| Library | Purpose |
|---------|---------|
| Room 2.6.1 + KSP | Entity/DAO code generation |
| SQLCipher 4.6.1 | Transparent SQLite encryption |
| BouncyCastle 1.78 | AES-256-GCM, PBKDF2, HMAC-SHA256 |
| Argon2 JVM 1.18.0 | Argon2id key derivation |
| DataStore Preferences | Settings (theme, auto-lock, password defaults, biometric) |
| Hilt 2.52 + KSP | Dependency injection |
| Robolectric 4.13 | JVM Android runtime for tests |
| MockK + Turbine | Mocking + Flow testing |

### 1.3 Data Layer

#### Entities (`data/local/entity/`)
- `VaultMetadataEntity` — singleton KDF params + vault version
- `GroupEntity` — groups/folders with tree support via `parentUuid`
- `EntryEntity` — title, username, URL unencrypted; **password + notes encrypted** as `ByteArray` ciphertext

#### DAOs (`data/local/dao/`)
- `VaultMetadataDao` — singleton CRUD + Flow observation
- `GroupDao` — CRUD, observe all, observe by parent
- `EntryDao` — CRUD, observe all, observe by group, observe favorites, search by title/username/tags

#### Database (`data/local/db/`)
- `VaultDatabase` — Room abstract class with SQLCipher `SupportFactory`

### 1.4 Crypto Manager (`crypto/CryptoManager.kt`)

**Key Derivation:**
- Argon2id (preferred): memory=64MB, iterations=3, parallelism=4
- PBKDF2-HMAC-SHA256 fallback: 600,000 iterations (OWASP 2023)
- Optional keyfile support: SHA-256(keyfile contents) composed with derived key

**Encryption:**
- AES-256-GCM with 12-byte random nonce + 128-bit tag
- `encrypt(key, plaintext, aad?)` → `nonce || ciphertext || tag`
- `decrypt(key, ciphertext, aad?)` → plaintext (throws `VaultCryptoException` on failure)

**Per-Entry Keys:**
- `deriveEntryKey(masterKey, entryUuid)` → HMAC-SHA256(masterKey, "VaultZero/Entry/1.0" + uuid)
- Each entry’s password and notes are encrypted with its own key (defense-in-depth beyond SQLCipher)

**Password Generator:**
- Configurable length (4–256), character sets (upper, lower, digits, symbols)
- Guarantees at least one char from each enabled set
- Optional ambiguous-character exclusion (0, O, 1, l, I)

**Secure Wipe:**
- `wipe(ByteArray?)` and `wipe(CharArray?)` — best-effort zero-fill
- JVM limitations: copies may remain in memory due to GC/copy-on-write

### 1.5 Repository (`data/repository/SqlCipherVaultRepository.kt`)

**Architecture:**
- SQLCipher encrypts the entire SQLite file with a passphrase derived from the master key
- KDF salt stored in a **plaintext sidecar file** (`vault.db.salt`) next to the DB
  - This avoids the chicken-and-egg of needing the DB open to read KDF params
  - Salt is not secret; it only prevents rainbow-table attacks
- SQLCipher passphrase = HMAC-SHA256(masterKey, "VaultZero/SQLCipher/v1")

**Lifecycle:**
- `createVault(password)` → derives key, creates DB, inserts metadata, unlocks
- `unlock(password)` → reads salt, derives key, opens SQLCipher DB, caches master key
- `lock()` → wipes master key, closes DB, emits `isUnlocked = false`

**CRUD:**
- Entries/groups returned as Flows; automatically empty when locked
- Entry passwords decrypted lazily via per-entry keys

**Settings:** Stored in DataStore (theme, auto-lock, password defaults, biometric enabled).

**Biometric Bridge:**
- `setBiometricKeyEncryptedDbKey(ByteArray)` — stores Keystore-encrypted DB key in DataStore
- `getBiometricKeyEncryptedDbKey()` / `clearBiometricKey()`
- Agent 3 must implement the actual Keystore + BiometricPrompt wiring

### 1.6 Use Cases (`domain/usecase/VaultUseCases.kt`)
Single-responsibility classes for Agent 3 ViewModels:
- `CreateVaultUseCase`
- `UnlockVaultUseCase`
- `LockVaultUseCase`
- `IsVaultCreatedUseCase`
- `ObserveVaultLockStateUseCase`
- `ObserveEntriesUseCase`
- `ObserveGroupsUseCase`
- `GetEntryUseCase`
- `SaveEntryUseCase`
- `DeleteEntryUseCase`
- `SaveGroupUseCase`
- `DeleteGroupUseCase`
- `GeneratePasswordUseCase`
- `ChangeMasterPasswordUseCase` — re-creates vault with new password (destructive; re-inserts all entries/groups)

### 1.7 Dependency Injection (`di/AppModule.kt`)
- `CryptoManager` as `@Singleton`
- `VaultRepository` → `SqlCipherVaultRepository`

### 1.8 Unit Tests
- `CryptoManagerTest.kt` — 15+ tests: key derivation consistency, PBKDF2 round-trip, AES-GCM integrity, tamper detection, password generator properties, secure wipe, entry key derivation
- `SqlCipherVaultRepositoryTest.kt` — Robolectric integration tests: create/unlock/lock, wrong password rejection, entry CRUD, group CRUD, Flow emissions

---

## 2. Build Instructions

```bash
cd /home/kriizo/projects/vaultzero

# Sync and build
./gradlew :app:assembleDebug

# Run unit tests (CryptoManager — pure JVM)
./gradlew :app:testDebugUnitTest

# Run Robolectric tests (Repository — Android simulation)
./gradlew :app:testDebugUnitTest --tests "*.SqlCipherVaultRepositoryTest"
```

---

## 3. Assumptions & Limitations

### 3.1 SQLCipher + Room
- SQLCipher native libs are bundled via AAR; loaded automatically by `SupportFactory`
- `abiFilters` include `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`
- Room schema version = 1; `exportSchema = true` (JSON in `app/schemas/`)

### 3.2 Memory Security
- `ByteArray` / `CharArray` are zeroed with `Arrays.fill`
- JVM provides no guarantee of complete erasure (GC may relocate arrays)
- `String` is NEVER used for passwords in the crypto layer
- The repository interface uses `String` for passwords for API convenience; the repository internally converts to `CharArray` before passing to `CryptoManager`

### 3.3 Keyfile Support
- The crypto manager supports an optional `keyfileBytes` parameter in `deriveMasterKey`
- The repository does NOT yet persist whether a keyfile was used; add a flag to `VaultMetadataEntity` if needed

### 3.4 Biometric Flow
- This layer provides the encrypted DB key storage bridge
- Agent 3 must:
  1. Generate a random AES key in Android Keystore with `setUserAuthenticationRequired(true)`
  2. Encrypt the master key (or derived passphrase) with that Keystore key
  3. Store the ciphertext via `setBiometricKeyEncryptedDbKey()`
  4. On biometric unlock: decrypt ciphertext with Keystore → unlock vault

### 3.5 Import / Export
- Stubs present (`exportVault`, `importVault`) but not implemented
- Planned format: `.vzb` (VaultZero Backup) — same encryption as live vault

### 3.6 Change Master Password
- `ChangeMasterPasswordUseCase` re-creates the entire vault file
- Biometric enrollment is cleared (must re-enroll)
- No automatic backup is created before re-creation (recommend adding)

---

## 4. API Contract for Agent 3 (UI / Biometrics)

### 4.1 Unlock Flow
```kotlin
val isVaultCreated = isVaultCreatedUseCase()
if (!isVaultCreated) {
    // Show "Create Vault" screen
    createVaultUseCase(password)
} else {
    // Show unlock screen
    when (val result = unlockVaultUseCase(password)) {
        is UnlockResult.Success -> navigateToHome()
        is UnlockResult.Error -> showError(result.message)
    }
}
```

### 4.2 Auto-Lock
```kotlin
// Observe lock state
observeVaultLockStateUseCase().collect { isUnlocked ->
    if (!isUnlocked) navigateToUnlock()
}

// Trigger lock from lifecycle
lockVaultUseCase() // call on onStop / screen off / timeout
```

### 4.3 Entry List
```kotlin
observeEntriesUseCase().collect { entries ->
    // Update UI; empty when locked
}
```

### 4.4 Password Generator
```kotlin
val password = generatePasswordUseCase(
    CryptoManager.PasswordConfig(length = 20, includeSymbols = true)
)
// Show password; offer copy to clipboard
// Call crypto.wipe(password) when done
```

### 4.5 Biometric Enrollment (Agent 3 implements)
```kotlin
// 1. User unlocks with master password
unlockVaultUseCase(password)

// 2. Generate Keystore key requiring biometric auth
val keyStore = KeyStore.getInstance("AndroidKeyStore")
val keyGen = KeyGenerator.getInstance("AES", "AndroidKeyStore")
keyGen.init(KeyGenParameterSpec.Builder("vaultzero_biometric", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setUserAuthenticationRequired(true)
    .setInvalidatedByBiometricEnrollment(true)
    .build())
val bioKey = keyGen.generateKey()

// 3. Encrypt master key with bioKey
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(Cipher.ENCRYPT_MODE, bioKey)
val encrypted = cipher.doFinal(masterKey)

// 4. Store in repository
repository.setBiometricKeyEncryptedDbKey(encrypted)
repository.setBiometricEnabled(true)
```

### 4.6 What Agent 3 Should NOT Touch
- Do NOT modify `CryptoManager` — it is a pure crypto primitive
- Do NOT change the SQLCipher passphrase derivation logic
- Do NOT store the master password in `SharedPreferences`, `DataStore`, or Keystore
- Do NOT use `String` for passwords in the crypto layer (repository handles conversion)

---

## 5. File Manifest

```
vaultzero/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── ARCHITECTURE.md                # Threat model + encryption scheme (pre-existing)
├── BUILDER_README.md              # This file
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/vaultzero/app/
│       │   │   ├── MainActivity.kt              # Navigation host (pre-existing stub)
│       │   │   ├── VaultZeroApplication.kt        # Hilt Application (pre-existing)
│       │   │   ├── crypto/
│       │   │   │   └── CryptoManager.kt           # NEW
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── dao/
│       │   │   │   │   │   ├── EntryDao.kt        # NEW
│       │   │   │   │   │   ├── GroupDao.kt        # NEW
│       │   │   │   │   │   └── VaultMetadataDao.kt # NEW
│       │   │   │   │   ├── db/
│       │   │   │   │   │   └── VaultDatabase.kt   # NEW
│       │   │   │   │   └── entity/
│       │   │   │   │       ├── EntryEntity.kt     # NEW
│       │   │   │   │       ├── GroupEntity.kt     # NEW
│       │   │   │   │       └── VaultMetadataEntity.kt # NEW
│       │   │   │   └── repository/
│       │   │   │       ├── InMemoryVaultRepository.kt  # Pre-existing (debug/placeholder)
│       │   │   │       └── SqlCipherVaultRepository.kt # NEW
│       │   │   ├── di/
│       │   │   │   └── AppModule.kt               # UPDATED (binds SqlCipherVaultRepository)
│       │   │   ├── domain/
│       │   │   │   ├── model/
│       │   │   │   │   └── VaultModels.kt         # Pre-existing (domain models)
│       │   │   │   ├── repository/
│       │   │   │   │   └── VaultRepository.kt     # Pre-existing (interface)
│       │   │   │   └── usecase/
│       │   │   │       └── VaultUseCases.kt       # NEW
│       │   │   └── presentation/                  # Pre-existing UI stubs (Agent 3's job)
│       │   └── res/                             # Pre-existing resources
│       └── test/
│           └── java/com/vaultzero/app/
│               ├── crypto/
│               │   └── CryptoManagerTest.kt       # NEW
│               └── data/
│                   └── repository/
│                       └── SqlCipherVaultRepositoryTest.kt # NEW
```

---

## 6. Security Checklist (Verified)

- [x] No `INTERNET` permission in manifest
- [x] `android:allowBackup="false"` in `<application>`
- [x] SQLCipher used for all SQLite access
- [x] Per-entry AES-256-GCM encryption for password + notes
- [x] Argon2id / PBKDF2 key derivation
- [x] Secure random for salts, nonces, UUIDs
- [x] Master key wiped on lock via `Arrays.fill`
- [x] Keystore bridge prepared for biometric auth
- [x] ProGuard rules configured (native libs preserved)

---

*Built by Agent 2 | VaultZero Core Layer v1.0*
