# VaultZero — Cryptography Specification

> Exact algorithms, libraries, constants, and memory-safety rules. No ambiguity. Copy-paste ready.

## 1. Algorithm Selection

### 1.1 Symmetric Encryption
| Parameter | Value | Fallback |
|-----------|-------|----------|
| Algorithm | AES-256-GCM | ChaCha20-Poly1305 |
| Key size | 256 bits | same |
| Nonce/IV | 12 bytes random | same |
| Tag size | 128 bits | same |

**Default:** AES-256-GCM via Android Keystore or Tink (`AesGcmKeyManager`).
**Fallback:** ChaCha20-Poly1305 via BouncyCastle `ChaCha20Poly1305` if builder prefers it and tests pass on target API levels (24+).

### 1.2 Key Derivation
| Parameter | Value | Fallback |
|-----------|-------|----------|
| Primary | Argon2id | PBKDF2-HMAC-SHA256 |
| Argon2id memory | 64 MB | 32 MB on low-RAM devices (<3 GB) |
| Argon2id iterations | 3 | same |
| Argon2id parallelism | 4 | same |
| Argon2id key length | 32 bytes | same |
| PBKDF2 iterations | 600_000 | same |
| PBKDF2 PRF | HMAC-SHA256 | same |
| Salt length | 16 bytes | same |

## 2. Libraries

### 2.1 Required Dependencies (build.gradle.kts)
```kotlin
dependencies {
    // SQLCipher for encrypted SQLite
    implementation("net.zetetic:android-database-sqlcipher:4.6.1")

    // Room (SQLCipher integration)
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Tink for AES-GCM key templates and Android Keystore
    implementation("com.google.crypto.tink:tink-android:1.14.0")

    // BouncyCastle for Argon2 and PBKDF2 fallback
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")

    // EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // BiometricPrompt
    implementation("androidx.biometric:biometric:1.1.0")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

### 2.2 ProGuard / R8 Rules
```proguard
-keep class net.sqlcipher.** { *; }
-keep class androidx.security.** { *; }
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * { @com.google.crypto.tink.* *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
```

## 3. Key Storage

### 3.1 Android Keystore
All Keystore keys generated with:
```kotlin
val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
val spec = KeyGenParameterSpec.Builder(
    alias,
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .setUserAuthenticationRequired(true)
    .setInvalidatedByBiometricEnrollment(true)
    .setRandomizedEncryptionRequired(true)
    .build()
keyGen.init(spec)
keyGen.generateKey()
```

### 3.2 StrongBox
Attempt StrongBox first (dedicated secure hardware). If unavailable, fall back to TEE.
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
    android.security.keystore.KeyGenParameterSpec.Builder(alias, purposes)
        .setIsStrongBoxBacked(true)
        .build().let { /* try init */ true }) {
    // Use StrongBox
}
```

### 3.3 EncryptedSharedPreferences
Store non-sensitive metadata (biometric enabled flag, auto-lock timeout, last export time).
**NEVER store:** master password, derived key, plaintext entry passwords.
```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val prefs = EncryptedSharedPreferences.create(
    context,
    "vaultzero_secure",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

## 4. Memory Safety Rules

### 4.1 Master Key Lifecycle
1. **Derivation:** `masterKey` returned as `ByteArray` from Argon2/PBKDF2.
2. **Session storage:** Wrap in `java.nio.ByteBuffer.allocateDirect()` (off-heap, harder to swap).
3. **Zeroization:**
   ```kotlin
   fun ByteArray.secureZero() {
       java.util.Arrays.fill(this, 0)
   }
   ```
   Native helper (optional, via JNI):
   ```c
   #include <string.h>
   JNIEXPORT void JNICALL
   Java_com_vaultzero_crypto_NativeCrypto_secureZero(JNIEnv*, jclass, jbyteArray arr) {
       jbyte* bytes = (*env)->GetByteArrayElements(env, arr, NULL);
       jsize len = (*env)->GetArrayLength(env, arr);
       memset(bytes, 0, (size_t)len);
       (*env)->ReleaseByteArrayElements(env, arr, bytes, JNI_COMMIT);
   }
   ```
4. **Access pattern:** Only `VaultSession` and `CryptoEngine` hold `masterKey`. UI layer never touches it.
5. **Garbage collection:** Do not rely on GC for clearing. Always explicit `secureZero()` before releasing the reference.

### 4.2 Password Input Fields
- Use `char[]` not `String` for password entry.
- Wipe `char[]` immediately after use.
- Disable keyboard suggestions and autocorrect:
  ```xml
  android:inputType="textPassword|textNoSuggestions"
  ```

### 4.3 Clipboard
```kotlin
fun copyToClipboard(context: Context, text: String, timeoutMs: Long = 30_000) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("password", text))
    CoroutineScope(Dispatchers.Main).launch {
        delay(timeoutMs)
        cm.clearPrimaryClip()  // API 28+, else set empty clip
    }
}
```

## 5. Randomness Sources

| Use Case | Source | Notes |
|----------|--------|-------|
| Salt generation | `java.security.SecureRandom` | 16 bytes |
| Nonce/IV generation | `SecureRandom` | 12 bytes |
| Biometric key | `KeyGenerator` (Keystore) | Hardware-backed if available |
| Entry UUID | `UUID.randomUUID()` | Version 4 |
| Password generation | `SecureRandom` | See §6 |

**Rule:** Never use `java.util.Random` or `kotlin.random.Random` for cryptographic material.

## 6. Password Generator

### 6.1 Character Sets
```kotlin
object CharSets {
    val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    val DIGITS    = "0123456789"
    val SYMBOLS   = "!@#$%^\u0026*()-_=+[]{}|;:,.\u003c>?"
    val AMBIGUOUS = "0O1lI"  // excluded by default
}
```

### 6.2 Algorithm
```kotlin
fun generatePassword(
    length: Int = 16,
    includeUpper: Boolean = true,
    includeLower: Boolean = true,
    includeDigits: Boolean = true,
    includeSymbols: Boolean = true,
    excludeAmbiguous: Boolean = true
): String {
    require(length >= 8) { "Minimum password length is 8" }
    val pool = buildString {
        if (includeUpper) append(CharSets.UPPERCASE)
        if (includeLower) append(CharSets.LOWERCASE)
        if (includeDigits) append(CharSets.DIGITS)
        if (includeSymbols) append(CharSets.SYMBOLS)
    }.let { if (excludeAmbiguous) it.filterNot(CharSets.AMBIGUOUS::contains) else it }
    require(pool.isNotEmpty()) { "At least one character set must be enabled" }

    val random = SecureRandom()
    val chars = CharArray(length)
    repeat(length) { i ->
        chars[i] = pool[random.nextInt(pool.length)]
    }
    return String(chars)
}
```

### 6.3 Entropy Requirements
| Length | Character Pool | Entropy |
|--------|---------------|---------|
| 16 | 94 (all printable ASCII) | ~105 bits |
| 20 | 94 | ~131 bits |
| 32 | 94 | ~210 bits |

Default: 16 characters, all sets enabled, ambiguous excluded. User configurable 8–64.

## 7. Import / Export Format

### 7.1 VaultZero Backup (`.vzb`)
Identical to live vault encryption. See ARCHITECTURE.md §6.

### 7.2 KeePass / KDBX Compatibility (Future)
Not required for MVP. If added later:
- Parse KDBX4 outer header (magic, version, transform rounds).
- Extract Argon2id or AES-KDF parameters.
- Decrypt with provided password, then re-encrypt into VaultZero native format.
- **Never store KDBX files as primary format** — import-only.

### 7.3 CSV Import (Future)
- Plaintext CSV → temporary in-memory parsing.
- Each row encrypted into native entry immediately.
- Wipe CSV bytes from memory after import.
- **Warn user:** CSV is plaintext; import from trusted source only.

## 8. Constants

```kotlin
object CryptoConstants {
    const val VAULT_MAGIC = "VZ00"
    const val VAULT_VERSION: Byte = 0x01
    const val KDF_ARGON2ID: Byte = 0x01
    const val KDF_PBKDF2: Byte   = 0x02

    const val SALT_LENGTH_BYTES = 16
    const val NONCE_LENGTH_BYTES = 12
    const val TAG_LENGTH_BITS = 128
    const val MASTER_KEY_LENGTH_BITS = 256

    const val ARGON2_DEFAULT_MEMORY_KB = 64 * 1024   // 64 MB
    const val ARGON2_MIN_MEMORY_KB = 32 * 1024       // 32 MB
    const val ARGON2_ITERATIONS = 3
    const val ARGON2_PARALLELISM = 4

    const val PBKDF2_ITERATIONS = 600_000
    const val PBKDF2_PRF = "HmacSHA256"

    const val HKDF_INFO_ENTRY_KEY = "VaultZero/Entry/1.0"
}
```

## 9. Test Vectors (Builder Validation)

### 9.1 Argon2id
```
Password: "password"
Salt (hex): 00000000000000000000000000000000
Memory: 64 MB, Iterations: 3, Parallelism: 4
Key length: 32
Expected key (hex):
512b391b6f1162975377090a78f27c8cbf243bad5e1267aeb9b07ae6de0e5c0e
```
(Use known-good reference: `argon2` command-line tool or Python `argon2-cffi`.)

### 9.2 AES-256-GCM
```
Key (hex): 0000000000000000000000000000000000000000000000000000000000000000
Nonce (hex): 000000000000000000000000
Plaintext: "The quick brown fox"
AAD (hex): 01000000000000000000000000000000
Expected ciphertext + tag (hex):
... (compute with OpenSSL or BouncyCastle reference)
```

## 10. References
- ARCHITECTURE.md — threat model, data lifecycle, backup design.
- DATA_MODEL.md — Room entities, SQLCipher schema.
- API_CONTRACT.md — Kotlin interfaces for Builder/Finisher.

---
*Version: 1.0 | VaultZero Codename*
