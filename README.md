[![CI](https://github.com/ChrisDesarden/vaultzero/actions/workflows/ci.yml/badge.svg)](https://github.com/ChrisDesarden/vaultzero/actions/workflows/ci.yml)
[![Latest Debug APK](https://github.com/ChrisDesarden/vaultzero/actions/workflows/release.yml/badge.svg)](https://github.com/ChrisDesarden/vaultzero/releases/tag/latest)

# VaultZero

A fully offline, open-source Android password manager. No cloud, no network permissions,
strong encryption, optional biometric unlock, and a Material Design 3 interface.

> **Status:** Functional prototype. The data layer is real (Room + SQLCipher + AES-GCM),
> the UI layer is built with Jetpack Compose, and the app runs on Android 8.0+ devices.

---

## Why VaultZero

Most password managers either store your secrets on someone else's server or are too complex
to audit. VaultZero takes the opposite approach:

- Your vault never leaves the device.
- The app requests **zero network permissions**.
- The code is small enough to read and reason about.
- Encryption primitives come from well-known, battle-tested libraries.

---

## Features

- **Offline only** — no `INTERNET` permission, no sync, no telemetry.
- **Strong encryption**
  - Master password is stretched with **Argon2id** (preferred) or **PBKDF2-HMAC-SHA512** fallback.
  - SQLCipher encrypts the SQLite database file.
  - Passwords and notes are encrypted a second time with per-entry **AES-256-GCM** keys.
- **Biometric unlock** — Android `BiometricPrompt` with master-password fallback.
- **Groups & entries** — organize credentials and search across them.
- **Password generator** — configurable length, character sets, and ambiguity exclusion.
- **Secure clipboard** — copied values auto-clear after 30 seconds.
- **Auto-lock / lockdown** — configurable timeout, manual lockdown, and background lock that
  returns you to the login screen.
- **Encrypted export / import** — back up the vault as a password-protected encrypted CSV
  that VaultZero can restore later.
- **Light & dark themes** — Material Design 3 with dynamic color support.

---

## Screenshots

| Unlock | Vault Home | Entry Detail | Settings |
|--------|------------|--------------|----------|
| ![Unlock](docs/screenshots/unlock.png) | ![Home](docs/screenshots/home.png) | ![Detail](docs/screenshots/detail.png) | ![Settings](docs/screenshots/settings.png) |

> Placeholder screenshots — add real ones under `docs/screenshots/`.

---

## Tech Stack

| Layer | Libraries |
|-------|-----------|
| UI | Jetpack Compose, Material Design 3, Navigation Compose |
| Dependency Injection | Hilt (Dagger) |
| Database | Room + SQLCipher |
| Crypto primitives | BouncyCastle `bcprov-jdk18on` (PBKDF2 + AES-GCM in tests), `javax.crypto` (AES-GCM on device) |
| KDF | Argon2id via `de.mkammerer:argon2-jvm` with PBKDF2 fallback |
| Settings | DataStore Preferences |
| Biometrics | `androidx.biometric:biometric` |
| Testing | JUnit, MockK, Turbine, Robolectric |

---

## Download the APK

The latest debug APK is built automatically on every push to `main`:

**[⬇ Download latest APK](https://github.com/ChrisDesarden/vaultzero/releases/download/latest/vaultzero-latest.apk)**

> The rolling release lives at the [`latest`](https://github.com/ChrisDesarden/vaultzero/releases/tag/latest) tag. It is rebuilt automatically by GitHub Actions, so the file above always points to the newest successful build.

### Install

1. Download `vaultzero-latest.apk` on your Android device.
2. Open the file from your notification or file manager.
3. If prompted, allow installation from this source.
4. Tap **Install**.

This is a debug build. For a production release, build and sign a release APK locally.

---

### Requirements

- Android Studio Ladybug or newer
- JDK 17+
- Android SDK API 35
- Kotlin 2.0.20

### Steps

```bash
git clone https://github.com/ChrisDesarden/vaultzero.git
cd vaultzero
./gradlew assembleDebug
```

Install the debug APK:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

---

## Architecture

```
Presentation                Domain                Data
┌──────────────────┐       ┌───────────────┐     ┌──────────────────────────┐
│ UnlockScreen       │◄────│ VaultRepository │◄────│ VaultRepositoryImpl        │
│ HomeScreen         │      │  (interface)    │     │  - Room + SQLCipher DB     │
│ EntryEditScreen    │      └───────────────┘     │  - DataStore settings      │
│ SettingsScreen     │                            │  - CryptoManager           │
└──────────────────┘                            └──────────────────────────┘
                                                           │
                                                           ▼
                                                    ┌───────────────┐
                                                    │ CryptoManager │
                                                    │ - Argon2id    │
                                                    │ - PBKDF2      │
                                                    │ - AES-256-GCM │
                                                    └───────────────┘
```

- **Presentation:** Compose screens observe `StateFlow`s from `ViewModel`s.
- **Domain:** Use cases mediate between UI and `VaultRepository`.
- **Data:** `VaultRepositoryImpl` coordinates Room entities, SQLCipher, DataStore, and
  `CryptoManager`.

### Security Model

1. **KDF:** the master password is stretched with Argon2id (or PBKDF2 fallback) using a
   random 16-byte salt stored in `vault.db.salt`.
2. **SQLCipher:** the database file is encrypted with a 256-bit key derived from the
   stretched master key.
3. **Per-entry encryption:** passwords and notes are encrypted again with per-entry keys
   derived via HMAC-SHA256(masterKey, "VaultZero/Entry/1.0" + entryUuid).
4. **Master key lifetime:** the derived master key is held in memory only while the vault is
   unlocked and is zero-filled in `lock()`.
5. **Biometrics:** a Keystore-backed AES/GCM key encrypts the database key; the ciphertext is
   stored in DataStore.
6. **Best-effort secure memory:** `CryptoManager.wipe()` overwrites `ByteArray`/`CharArray`
   contents. On Android this is best-effort due to GC and copy-on-write.

---

## Credits & Inspiration

VaultZero is a learning/educational project and owes a great deal to existing open-source
password managers and libraries:

- **Password Safe** — the original concept of a small, offline, user-controlled password
  database. VaultZero borrows the *philosophy*, not the code.
  - Website: https://pwsafe.org/
  - License: Artistic License 2.0

- **KeePass / KeePassDX** — inspiration for group-based organization, per-entry encryption,
  and offline-first design.
  - KeePass website: https://keepass.info/
  - KeePassDX repository: https://github.com/Kunzisoft/KeePassDX
  - License: GPL-3.0

- **AndroidX / Jetpack Compose** — the entire UI toolkit.
  - Repository: https://github.com/androidx/androidx
  - License: Apache-2.0

- **Hilt** — dependency injection.
  - Repository: https://github.com/google/dagger
  - License: Apache-2.0

- **Room & SQLCipher** — local database and transparent database encryption.
  - SQLCipher: https://github.com/sqlcipher/sqlcipher (BSD-style)
  - Zetetic SQLCipher Android bindings: https://www.zetetic.net/sqlcipher/

- **BouncyCastle** — PBKDF2 and AES-GCM primitives used in unit tests.
  - Website: https://www.bouncycastle.org/
  - License: BouncyCastle License (MIT-like)

- **Argon2-JVM** — Argon2id implementation.
  - Repository: https://github.com/phxql/argon2-jvm
  - License: MIT

---

## License

VaultZero is licensed under the [GNU General Public License v3.0](LICENSE).

```
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```

---

## Contributing

1. Fork the repository and create a feature branch.
2. Keep changes focused; add tests where possible.
3. Run `./gradlew testDebugUnitTest` before opening a PR.
4. Be respectful — this is a security-sensitive project.

### Reporting Security Issues

Please open a **private** issue or contact the maintainers directly. Do not disclose
vulnerabilities publicly until a fix is released.

---

## Roadmap

- [ ] KeePass `.kdbx` import/export compatibility.
- [ ] TOTP code generation for entries.
- [ ] Attachments support for entries.
- [ ] Android 16 KB page-size compatibility fixes.
- [ ] Wear OS companion unlock.

---

Made with ❤️ for privacy.
