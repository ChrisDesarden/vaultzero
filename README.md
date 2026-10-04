# VaultZero

A completely offline, open-source Android password manager inspired by Password Safe and KeePassDX.

VaultZero keeps your credentials under your control: no cloud, no network permissions, strong encryption, and optional biometric unlocking.

---

## Features

- **Offline only** — zero network permissions.
- **Strong encryption** — Keystore-backed keys wrap the database key.
- **Biometric unlock** — Android BiometricPrompt with master-password fallback.
- **Groups & entries** — organize passwords into groups and search across them.
- **Password generator** — configurable length, character sets, and ambiguity exclusion.
- **Secure clipboard** — copied values auto-clear after 30 seconds.
- **Auto-lock** — configurable inactivity timeout.
- **Light & dark themes** — Material Design 3 with dynamic theming.
- **Export / import** — backup and restore your encrypted vault locally.

---

## Screenshots

| Unlock | Vault Home | Entry Detail | Settings |
|--------|------------|--------------|----------|
| ![Unlock](docs/screenshots/unlock.png) | ![Home](docs/screenshots/home.png) | ![Detail](docs/screenshots/detail.png) | ![Settings](docs/screenshots/settings.png) |

> Placeholder screenshots — add your own under `docs/screenshots/`.

---

## Build Instructions

### Requirements

- Android Studio Ladybug or newer
- JDK 17+
- Android SDK with API 35
- Kotlin 2.x

### Steps

```bash
git clone https://github.com/yourusername/vaultzero.git
cd vaultzero
./gradlew assembleDebug
```

Install the debug APK:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Run unit tests:

```bash
./gradlew test
```

Run instrumentation tests:

```bash
./gradlew connectedAndroidTest
```

---

## Architecture

- **UI:** Jetpack Compose + Material Design 3 + Jetpack Navigation Compose.
- **DI:** Hilt wires `ViewModel`s to `Repository`/`UseCase` classes.
- **State:** UI state flows from `ViewModel` via `StateFlow`; one-time events via `SharedFlow`.
- **Biometrics:** `androidx.biometric:biometric` + Keystore-backed AES/GCM key.
- **Clipboard:** `SecureClipboard` clears the primary clip after a timeout.

The repository layer is intentionally stubbed as `InMemoryVaultRepository` so Agent 2 can later swap in the real encrypted database implementation without touching the UI.

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

We welcome contributions! Please follow these guidelines:

1. Fork the repository and create a feature branch.
2. Keep changes focused and add tests where possible.
3. Ensure `./gradlew test` passes locally.
4. Open a pull request with a clear description of the change.
5. Be respectful — this is a security-sensitive project; review carefully.

### Security

If you discover a security issue, please open a **private** issue or email the maintainers directly. Do not disclose vulnerabilities publicly until a fix is released.

---

## Roadmap

- [ ] Replace in-memory repository with encrypted SQLite/Room backend.
- [ ] Add KeePass `.kdbx` import/export compatibility.
- [ ] TOTP code generation for entries.
- [ ] Attachments support for entries.
- [ ] Wear OS companion unlock.

---

Made with ❤️ for privacy.
