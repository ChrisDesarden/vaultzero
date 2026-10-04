# VaultZero — Architecture & Threat Model

> Offline-first Android password manager. This document is the single source of truth for Builder and Finisher agents.

## 1. Threat Model

### Assumptions
- The device is uncompromised at first unlock.
- The attacker can obtain:
  - The encrypted vault file (backup, ADB pull, file-level access).
  - The app’s internal storage (rooted device, forensic extraction).
  - A memory dump while the app is running.
- The attacker **cannot**:
  - Break AES-256-GCM or ChaCha20-Poly1305 without the key.
  - Forge an authentic Argon2id-derived key without the master password + keyfile.
  - Extract a hardware-bound Keystore key from the TEE/StrongBox.

### Assets
| Asset | Protection Goal |
|-------|-----------------|
| Vault database | Confidentiality + Integrity |
| Master key | Never persisted in application memory longer than necessary |
| Biometric token | Unwraps Keystore key only; no password material |
| Exported backups | Encrypted with same vault key, authenticated |
| Clipboard entries | Cleared after configurable timeout (default 30s) |

### Threats & Mitigations
| Threat | Mitigation |
|--------|------------|
| Brute-force master password | Argon2id (tune for ~500ms on target device) |
| Tampered vault file | Authenticated encryption (GCM/Poly1305 tags) |
| Memory dump of running app | `SecureZeroMemory` equivalent + short key lifetime |
| Root access to internal storage | EncryptedSharedPreferences + SQLCipher for DB |
| Biometric bypass / replay | Keystore key requires user auth (biometric or device credential) |
| Clipboard leak | Auto-clear timer; optional disable clipboard copy |
| Screen recording | `FLAG_SECURE` on sensitive activities |
| Backup exposure | Disallow Android auto-backup; manual encrypted export only |

## 2. Encryption Scheme

### 2.1 Master Key Derivation (MKD)
```
masterKey = Argon2id(
    password   = UTF-8 bytes of user master password,
    salt       = 16 random bytes (stored in vault header),
    memory     = 64 MB  (min 32 MB; scale up on high-end devices),
    iterations = 3,
    parallelism= 4,
    keyLength  = 32 bytes (256 bits)
)
```
If Argon2id library is unavailable (e.g., pure Java fallback), use:
```
masterKey = PBKDF2-HMAC-SHA256(
    password,
    salt,
    iterations = 600_000 (OWASP 2023),
    keyLength  = 32 bytes
)
```
**Decision rule:** Try Argon2id first via JNI / BouncyCastle. If native load fails at runtime, fall back to PBKDF2 and log a one-time warning.

### 2.2 Keyfile Support
If a keyfile is provided:
```
keyfileBytes = SHA-256( entire keyfile contents )
combinedKey  = SHA-256( masterKey || keyfileBytes )
```
The combined key becomes the AES-256 key. The keyfile itself is never stored.

### 2.3 Vault Encryption
- **Algorithm:** AES-256-GCM (default). ChaCha20-Poly1305 acceptable if Tink or BouncyCastle supports it and the builder prefers it.
- **Key:** 256-bit derived master key (or combined key with keyfile).
- **IV/Nonce:** 12 bytes random per encryption operation.
- **Tag:** 128-bit GCM authentication tag (appended to ciphertext).
- **Additional Data (AAD):** Vault version byte + salt (prevents downgrade/replay).

### 2.4 Vault Header Layout (first 64 bytes of vault file)
| Offset | Size | Field |
|--------|------|-------|
| 0 | 4 | Magic: `VZ00` |
| 4 | 1 | Version: `0x01` |
| 5 | 1 | KDF type: `0x01`=Argon2id, `0x02`=PBKDF2 |
| 6 | 2 | Reserved |
| 8 | 16 | KDF salt |
| 24 | 4 | Argon2 memory (KB) or PBKDF2 iterations (lo32) |
| 28 | 4 | Argon2 parallelism or PBKDF2 iterations (hi32) |
| 32 | 32 | Encrypted payload starts at byte 32 (GCM ciphertext + tag) |

## 3. Biometric Flow

### 3.1 Principle
The master password is **never** stored in plaintext, not in Keystore, not in memory longer than the unlock operation.

### 3.2 Enrollment
1. User unlocks with master password (or password+keyfile).
2. App generates a random 256-bit **biometric key** (`bioKey`).
3. `bioKey` is stored in Android Keystore with:
   - `setUserAuthenticationRequired(true)`
   - `setInvalidatedByBiometricEnrollment(true)` (API 24+)
   - `setBlockMode(BlockMode.GCM)`
   - `setEncryptionPaddings(Padding.NoPadding)`
4. `bioKey` encrypts the **derived master key**:
   ```
   encryptedMasterKey = AES-256-GCM(bioKey, masterKey)
   ```
   `encryptedMasterKey` is stored in EncryptedSharedPreferences.

### 3.3 Unlock
1. User triggers biometric prompt.
2. Keystore decrypts `bioKey` after successful biometric auth.
3. App decrypts `masterKey` using `bioKey`.
4. `masterKey` is held in a `SecretKeyHandle` (char[] or `ByteBuffer` in native memory) for the session.
5. On app background or timeout, `masterKey` is wiped (`Arrays.fill` + native `SecureZeroMemory` if available).

### 3.4 Revocation
- On biometric enrollment change: Keystore key invalidated → require master password re-entry.
- User can disable biometric unlock in Settings → delete Keystore key + wipe `encryptedMasterKey`.

## 4. Vault Schema

### 4.1 SQLCipher Database (Room abstraction)
Room entities stored in a SQLCipher-encrypted SQLite database. The SQLCipher key is the derived master key.

#### Tables
```sql
-- vault_meta: singleton row with vault-level metadata
CREATE TABLE vault_meta (
    id              INTEGER PRIMARY KEY CHECK (id = 1),
    kdf_type        INTEGER NOT NULL,  -- 1=Argon2id, 2=PBKDF2
    kdf_salt        BLOB    NOT NULL,
    kdf_params      BLOB    NOT NULL, -- JSON: {memory, iterations, parallelism}
    created_at      INTEGER NOT NULL, -- epoch millis
    updated_at      INTEGER NOT NULL,
    version         INTEGER NOT NULL   -- schema version
);

-- entries: password entries
CREATE TABLE entries (
    uuid            BLOB PRIMARY KEY, -- 16 bytes, random
    title           TEXT    NOT NULL,
    username        TEXT,
    password        BLOB    NOT NULL,   -- encrypted with per-entry key (see 4.2)
    url             TEXT,
    notes           BLOB,              -- encrypted
    tags            TEXT,              -- comma-separated for queryability
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL,
    favorite        INTEGER NOT NULL DEFAULT 0
);

-- folders: grouping (optional MVP, required for v1)
CREATE TABLE folders (
    uuid        BLOB PRIMARY KEY,
    name        TEXT NOT NULL,
    parent_uuid BLOB REFERENCES folders(uuid),
    created_at  INTEGER NOT NULL
);

-- entry_folder: many-to-many
CREATE TABLE entry_folder (
    entry_uuid  BLOB NOT NULL REFERENCES entries(uuid) ON DELETE CASCADE,
    folder_uuid BLOB NOT NULL REFERENCES folders(uuid) ON DELETE CASCADE,
    PRIMARY KEY (entry_uuid, folder_uuid)
);

-- audit_log: local-only, no network sync
CREATE TABLE audit_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    action      TEXT NOT NULL,         -- CREATE, UPDATE, DELETE, EXPORT, UNLOCK, etc.
    entity_uuid BLOB,                   -- affected entry uuid, if any
    timestamp   INTEGER NOT NULL,
    detail      TEXT                    -- JSON blob, no sensitive data
);
```

### 4.2 Per-Entry Encryption
To limit blast radius if a single entry key is compromised in memory:
- Each entry has a **per-entry key** (`entryKey`) = HKDF-SHA256(masterKey, salt=entryUUID, info="VaultZero/Entry/1.0").
- `entry.password` and `entry.notes` are encrypted with `entryKey` using AES-256-GCM.
- `entryKey` is derived on demand, never stored.

### 4.3 In-Memory Representation
- `VaultSession` holds `masterKey: ByteArray` only while unlocked.
- All entry passwords decrypted lazily (decrypt on read, re-encrypt on write).
- UI layer never holds plaintext passwords longer than display duration.

## 5. Data Lifecycle

### 5.1 Creation
1. User sets master password (+ optional keyfile).
2. App generates salt, derives key, creates vault header + SQLCipher DB.
3. Initial `vault_meta` row inserted.
4. App immediately auto-locks (no entries yet).

### 5.2 Unlock → Use → Lock
1. Unlock derives/caches `masterKey` in `VaultSession`.
2. Room DAOs operate normally; SQLCipher handles transparent encryption.
3. Per-entry fields decrypted lazily by repository layer.
4. **Auto-lock triggers:**
   - App moves to background (`onStop()` without `onResume()` within timeout).
   - Screen off (`ACTION_SCREEN_OFF`).
   - Timeout expires (configurable: 30s / 1m / 5m / 10m / never).
5. Lock action: wipe `masterKey`, clear `VaultSession`, return to UnlockActivity.

### 5.3 Destruction
- Uninstall: Android deletes app data. No remote wipe possible (offline by design).
- User-initiated vault deletion: overwrite vault file with random data (3 passes), then delete.

## 6. Backup / Restore Design

### 6.1 Export Format
- File extension: `.vzb` (VaultZero Backup).
- Content: same vault header + encrypted payload as live vault.
- Additional trailer (unencrypted) after payload:
  - Export timestamp (8 bytes, epoch millis).
  - App version string (null-terminated, max 32 bytes).
- The backup is **identical encryption** to the live vault — the same derived key unlocks it. No separate backup password (avoids confusion).

### 6.2 Import
1. User selects `.vzb` file via Storage Access Framework (SAF).
2. App validates magic + version.
3. Prompts for master password (and keyfile if the vault header indicates keyfile was used).
4. Derives key, decrypts payload, verifies GCM tag.
5. Replaces current vault DB atomically:
   - Write to temp file next to vault.
   - fsync temp.
   - Rename over live vault.
   - Reopen SQLCipher with new key.

### 6.3 Restore Safety
- Import warns if existing vault will be overwritten.
- Optional: keep last N automatic backups in app-private `backups/` directory (rotated, max 3 × 10MB).

## 7. Security Checklist for Builder

- [ ] `android.permission.INTERNET` is **absent** from manifest.
- [ ] `android:allowBackup="false"` in `<application>`.
- [ ] SQLCipher used for all SQLite access; no plaintext fallback.
- [ ] `FLAG_SECURE` set on activities showing passwords.
- [ ] Clipboard cleared via `ClipboardManager.clearPrimaryClip()` after timeout.
- [ ] Keystore key requires `setUserAuthenticationRequired(true)`.
- [ ] Master password input uses `android:inputType="textPassword"` with no autocomplete.
- [ ] Crash logs must NOT include password material or key bytes.
- [ ] ProGuard/R8 rules keep `sqlcipher` native libs and crypto classes.

## 8. References
- CRYPTO_SPEC.md — exact algorithms, libraries, constants.
- DATA_MODEL.md — Room entities, DAOs, migration strategy.
- API_CONTRACT.md — public interfaces for Builder/Finisher.

---
*Version: 1.0 | VaultZero Codename*
